import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent, type Crumb } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { DateTimePipe, RelativeTimePipe } from '../../../../shared/pipes/format.pipes';
import { AccountStatusTagComponent } from '../../components/account-status-tag/account-status-tag';
import { TeacherFormDialogComponent } from '../../components/teacher-form-dialog/teacher-form-dialog';
import { AdminTeacherService } from '../../data/admin-teacher.service';

/**
 * Detalhe de um professor — Parte 6, §5.
 *
 * Deixa explícito que o e-mail não é editável e que o admin nunca define a
 * senha de ninguém: o que existe é o envio de um link.
 */
@Component({
  selector: 'cc-teacher-detail-page',
  imports: [
    Button,
    Tag,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    AccountStatusTagComponent,
    TeacherFormDialogComponent,
    DateTimePipe,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './teacher-detail-page.html',
  styleUrl: './teacher-detail-page.scss',
})
export class TeacherDetailPage {
  private readonly teachers = inject(AdminTeacherService);
  private readonly notify = inject(NotificationService);

  readonly teacherId = input.required<string>();

  protected readonly state = createPageState(() => this.teachers.get(this.teacherId()));
  protected readonly dialogVisible = signal(false);

  private readonly guard = createSubmitGuard();
  protected readonly busy = this.guard.submitting;

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Professores', link: '/admin/professores' },
    { label: this.state.data()?.fullName ?? 'Professor' },
  ]);

  constructor() {
    effect(() => {
      this.teacherId();
      void this.state.load();
    });
  }

  protected async onSaved(): Promise<void> {
    this.dialogVisible.set(false);
    await this.state.refresh();
  }

  protected async toggleActive(): Promise<void> {
    const teacher = this.state.data();
    if (!teacher) {
      return;
    }

    const isInactive = teacher.status === 'INACTIVE';

    const confirmed = isInactive
      ? await this.notify.confirm({
          header: `Reativar ${teacher.fullName}?`,
          message: 'O professor volta a conseguir entrar e gerenciar as salas dele.',
          acceptLabel: 'Reativar',
          icon: 'pi pi-check-circle',
        })
      : await this.notify.destructive({
          header: `Desativar ${teacher.fullName}?`,
          message:
            'As sessões ativas serão encerradas **imediatamente** — se ele estiver usando o sistema agora, será desconectado. As salas e o conteúdo dele são preservados.',
          acceptLabel: 'Desativar',
        });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      try {
        await (isInactive
          ? this.teachers.activate(teacher.id)
          : this.teachers.deactivate(teacher.id));
        this.notify.success(isInactive ? 'Professor reativado' : 'Professor desativado');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível concluir a ação.');
      }
    });
  }

  protected async sendReset(): Promise<void> {
    const teacher = this.state.data();
    if (!teacher) {
      return;
    }

    const confirmed = await this.notify.confirm({
      header: 'Enviar link de redefinição?',
      message: `Enviaremos para ${teacher.email} um link para ele definir uma nova senha. Você não vê nem escolhe a senha dele.`,
      acceptLabel: 'Enviar link',
      icon: 'pi pi-envelope',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      try {
        await this.teachers.sendPasswordReset(teacher.id);
        this.notify.success('Link enviado', `Instruções a caminho de ${teacher.email}.`);
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível enviar o link.');
      }
    });
  }
}
