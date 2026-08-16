import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Paginator, type PaginatorState } from 'primeng/paginator';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { ActiveTagComponent } from '../../components/active-tag/active-tag';
import { InstitutionFormDialogComponent } from '../../components/institution-form-dialog/institution-form-dialog';
import { createListQuery } from '../../data/list-query';
import { InstitutionService } from '../../data/institution.service';
import type { AdminInstitution } from '../../models/institution-dto';
import { CnpjPipe, PhonePipe } from '../../util/admin-format.pipes';

/**
 * Lista de instituições — Parte 6, §4.
 *
 * Busca e filtro vivem na **query string**: é o que faz o cartão do painel
 * apontar para "instituições inativas" com um link compartilhável, e o que
 * mantém o filtro depois de uma recarga ou do botão "voltar".
 */
@Component({
  selector: 'cc-institutions-page',
  imports: [
    FormsModule,
    RouterLink,
    Button,
    InputText,
    Select,
    Paginator,
    TableModule,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    ActiveTagComponent,
    InstitutionFormDialogComponent,
    CnpjPipe,
    PhonePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './institutions-page.html',
  styleUrl: './institutions-page.scss',
})
export class InstitutionsPage {
  private readonly institutions = inject(InstitutionService);
  private readonly notify = inject(NotificationService);

  protected readonly query = createListQuery();
  protected readonly search = this.query.text('search');
  protected readonly active = this.query.flag('active');

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<AdminInstitution | null>(null);
  protected readonly busyId = signal<string | null>(null);

  private readonly guard = createSubmitGuard();

  protected readonly activeOptions = [
    { value: null, label: 'Todas' },
    { value: 'true', label: 'Ativas' },
    { value: 'false', label: 'Inativas' },
  ];

  protected readonly state = createPageState(() =>
    this.institutions.list(
      { search: this.search() || undefined, active: this.active() },
      this.query.pageQuery(),
    ),
  );

  constructor() {
    effect(() => {
      this.query.params();
      void this.state.load();
    });
  }

  protected currentActiveOption(): string | null {
    const value = this.active();
    return value === undefined ? null : String(value);
  }

  protected onSearch(value: string): void {
    this.query.setFilters({ search: value || null }, true);
  }

  protected onActiveChange(value: string | null): void {
    this.query.setFilters({ active: value });
  }

  protected onPage(event: PaginatorState): void {
    this.query.setPage({ page: event.page ?? 0, size: event.rows ?? 20 });
  }

  protected first(): number {
    const page = this.state.data();
    return page ? page.page * page.size : 0;
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  protected openEdit(institution: AdminInstitution): void {
    this.editing.set(institution);
    this.dialogVisible.set(true);
  }

  protected async onSaved(): Promise<void> {
    this.dialogVisible.set(false);
    await this.state.refresh();
  }

  protected async activate(institution: AdminInstitution): Promise<void> {
    const confirmed = await this.notify.confirm({
      header: `Ativar "${institution.name}"?`,
      message: 'A instituição volta a aceitar novos professores, alunos e salas.',
      acceptLabel: 'Ativar',
      icon: 'pi pi-check-circle',
    });

    if (confirmed) {
      await this.run(institution.id, () => this.institutions.activate(institution.id), 'Instituição ativada');
    }
  }

  protected async deactivate(institution: AdminInstitution): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Desativar "${institution.name}"?`,
      // O efeito real: bloqueia o novo, preserva o existente.
      message:
        'A instituição deixa de aceitar **novos** professores, alunos e salas. Os vínculos que já existem continuam funcionando normalmente — ninguém perde acesso.',
      acceptLabel: 'Desativar',
    });

    if (confirmed) {
      await this.run(
        institution.id,
        () => this.institutions.deactivate(institution.id),
        'Instituição desativada',
      );
    }
  }

  protected async remove(institution: AdminInstitution): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Excluir "${institution.name}"?`,
      message:
        'Esta ação não pode ser desfeita. Só é possível excluir instituições sem nenhum vínculo — se houver professores ou alunos, use "desativar".',
      acceptLabel: 'Excluir',
    });

    if (confirmed) {
      await this.run(institution.id, () => this.institutions.remove(institution.id), 'Instituição excluída');
    }
  }

  private async run(id: string, action: () => Promise<unknown>, success: string): Promise<void> {
    await this.guard.run(async () => {
      this.busyId.set(id);
      try {
        await action();
        this.notify.success(success);
        await this.state.refresh();
      } catch (error) {
        this.handleFailure(error);
      } finally {
        this.busyId.set(null);
      }
    });
  }

  private handleFailure(error: unknown): void {
    if (error instanceof ApiError && error.status === 409) {
      // Instituição com histórico não some: o caminho é desativar.
      this.notify.warn(
        'Esta instituição tem vínculos',
        'Há professores, alunos ou salas ligados a ela. Desative-a em vez de excluir — nada é perdido.',
      );
      return;
    }
    this.notify.error(error instanceof ApiError ? error : 'Não foi possível concluir a ação.');
  }
}
