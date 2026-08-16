import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Paginator, type PaginatorState } from 'primeng/paginator';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { ApiError } from '../../../../core/api/problem-details';
import type { AccountStatus } from '../../../../core/models/enums';
import { ACCOUNT_STATUS_LABELS } from '../../../../core/models/labels';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { AccountStatusTagComponent } from '../../components/account-status-tag/account-status-tag';
import { TeacherFormDialogComponent } from '../../components/teacher-form-dialog/teacher-form-dialog';
import { AdminTeacherService } from '../../data/admin-teacher.service';
import { createListQuery } from '../../data/list-query';
import type { AdminTeacher } from '../../models/teacher-dto';

const STATUSES: readonly AccountStatus[] = ['PENDING', 'ACTIVE', 'INACTIVE'];

/**
 * Lista de professores — Parte 6, §5.
 *
 * **Nenhuma tela aqui define senha.** Criar um professor gera uma conta
 * `PENDING` e dispara o convite; ele mesmo define a própria senha (§8.1 da
 * spec de integração).
 */
@Component({
  selector: 'cc-teachers-page',
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
    AccountStatusTagComponent,
    TeacherFormDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './teachers-page.html',
  styleUrl: './teachers-page.scss',
})
export class TeachersPage {
  private readonly teachers = inject(AdminTeacherService);
  private readonly notify = inject(NotificationService);

  protected readonly query = createListQuery();
  protected readonly search = this.query.text('search');
  protected readonly status = this.query.option<AccountStatus>('status', STATUSES);

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<AdminTeacher | null>(null);
  protected readonly busyId = signal<string | null>(null);

  private readonly guard = createSubmitGuard();

  protected readonly statusOptions = [
    { value: null, label: 'Todas as situações' },
    ...STATUSES.map((value) => ({ value, label: ACCOUNT_STATUS_LABELS[value] })),
  ];

  protected readonly state = createPageState(() =>
    this.teachers.list(
      { search: this.search() || undefined, status: this.status() },
      this.query.pageQuery(),
    ),
  );

  constructor() {
    effect(() => {
      this.query.params();
      void this.state.load();
    });
  }

  protected onSearch(value: string): void {
    this.query.setFilters({ search: value || null }, true);
  }

  protected onStatusChange(value: string | null): void {
    this.query.setFilters({ status: value });
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

  protected openEdit(teacher: AdminTeacher): void {
    this.editing.set(teacher);
    this.dialogVisible.set(true);
  }

  protected async onSaved(): Promise<void> {
    this.dialogVisible.set(false);
    await this.state.refresh();
  }

  protected async activate(teacher: AdminTeacher): Promise<void> {
    const confirmed = await this.notify.confirm({
      header: `Reativar ${teacher.fullName}?`,
      message: 'O professor volta a conseguir entrar e gerenciar as salas dele.',
      acceptLabel: 'Reativar',
      icon: 'pi pi-check-circle',
    });

    if (confirmed) {
      await this.run(teacher.id, () => this.teachers.activate(teacher.id), 'Professor reativado');
    }
  }

  protected async deactivate(teacher: AdminTeacher): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Desativar ${teacher.fullName}?`,
      // O efeito é imediato e visível para quem está usando: precisa ser dito.
      message:
        'As sessões ativas serão encerradas **imediatamente** — se ele estiver usando o sistema agora, será desconectado. As salas e o conteúdo dele são preservados.',
      acceptLabel: 'Desativar',
    });

    if (confirmed) {
      await this.run(teacher.id, () => this.teachers.deactivate(teacher.id), 'Professor desativado');
    }
  }

  protected async sendReset(teacher: AdminTeacher): Promise<void> {
    const confirmed = await this.notify.confirm({
      header: 'Enviar link de redefinição?',
      message: `Enviaremos para ${teacher.email} um link para ele definir uma nova senha. Você não vê nem escolhe a senha dele.`,
      acceptLabel: 'Enviar link',
      icon: 'pi pi-envelope',
    });

    if (confirmed) {
      await this.run(
        teacher.id,
        () => this.teachers.sendPasswordReset(teacher.id),
        'Link enviado',
      );
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
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível concluir a ação.');
      } finally {
        this.busyId.set(null);
      }
    });
  }
}
