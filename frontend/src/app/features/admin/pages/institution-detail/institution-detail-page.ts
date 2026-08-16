import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Button } from 'primeng/button';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent, type Crumb } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { DateTimePipe } from '../../../../shared/pipes/format.pipes';
import { ActiveTagComponent } from '../../components/active-tag/active-tag';
import { InstitutionFormDialogComponent } from '../../components/institution-form-dialog/institution-form-dialog';
import { InstitutionService } from '../../data/institution.service';
import { CnpjPipe, PhonePipe } from '../../util/admin-format.pipes';

/**
 * Detalhe de uma instituição — Parte 6, §4.
 *
 * Mostra o resumo de vínculos, quando a API o fornece: é ele que explica por
 * que a exclusão pode estar indisponível e por que desativar é o caminho.
 */
@Component({
  selector: 'cc-institution-detail-page',
  imports: [
    Button,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    ActiveTagComponent,
    InstitutionFormDialogComponent,
    DateTimePipe,
    CnpjPipe,
    PhonePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './institution-detail-page.html',
  styleUrl: './institution-detail-page.scss',
})
export class InstitutionDetailPage {
  private readonly institutions = inject(InstitutionService);
  private readonly notify = inject(NotificationService);

  readonly institutionId = input.required<string>();

  protected readonly state = createPageState(() => this.institutions.get(this.institutionId()));
  protected readonly dialogVisible = signal(false);

  private readonly guard = createSubmitGuard();
  protected readonly busy = this.guard.submitting;

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Instituições', link: '/admin/instituicoes' },
    { label: this.state.data()?.name ?? 'Instituição' },
  ]);

  constructor() {
    effect(() => {
      this.institutionId();
      void this.state.load();
    });
  }

  protected async onSaved(): Promise<void> {
    this.dialogVisible.set(false);
    await this.state.refresh();
  }

  protected async toggleActive(): Promise<void> {
    const institution = this.state.data();
    if (!institution) {
      return;
    }

    const confirmed = institution.active
      ? await this.notify.destructive({
          header: `Desativar "${institution.name}"?`,
          message:
            'A instituição deixa de aceitar **novos** professores, alunos e salas. Os vínculos que já existem continuam funcionando — ninguém perde acesso.',
          acceptLabel: 'Desativar',
        })
      : await this.notify.confirm({
          header: `Ativar "${institution.name}"?`,
          message: 'A instituição volta a aceitar novos professores, alunos e salas.',
          acceptLabel: 'Ativar',
          icon: 'pi pi-check-circle',
        });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      try {
        await (institution.active
          ? this.institutions.deactivate(institution.id)
          : this.institutions.activate(institution.id));
        this.notify.success(institution.active ? 'Instituição desativada' : 'Instituição ativada');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível concluir a ação.');
      }
    });
  }
}
