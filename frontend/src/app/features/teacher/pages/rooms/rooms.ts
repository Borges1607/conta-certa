import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Button } from 'primeng/button';

import { ApiError } from '../../../../core/api/problem-details';
import type { PageQuery } from '../../../../core/models/page';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { RoomFormDialogComponent } from '../../components/room-form-dialog/room-form-dialog';
import { TeacherRoomService } from '../../data/teacher-room.service';
import type { TeacherRoomSummary } from '../../models/room';
import { RoomListCardComponent } from '../../components/room-list-card/room-list-card';

/**
 * Lista de salas — Parte 5, §4.
 *
 * Toda ação destrutiva passa por confirmação com o **efeito real** descrito:
 * arquivar deixa a sala somente leitura, regenerar código invalida o anterior
 * sem mexer nas matrículas, e excluir só existe quando a API diz que a sala
 * nunca foi usada.
 */
@Component({
  selector: 'cc-teacher-rooms-page',
  imports: [
    Button,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    RoomFormDialogComponent,
    RoomListCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rooms.html',
  styleUrl: './rooms.scss',
})
export class TeacherRoomsPage {
  private readonly rooms = inject(TeacherRoomService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly query = signal<PageQuery>({ page: 0, size: 50 });

  protected readonly state = createPageState(() => this.rooms.list(this.query()));

  protected readonly formVisible = signal(false);
  protected readonly editing = signal<TeacherRoomSummary | null>(null);
  protected readonly busyRoomId = signal<string | null>(null);

  private readonly actionGuard = createSubmitGuard();

  constructor() {
    void this.state.load();

    // O painel manda `?nova=1` quando o professor clica em "criar primeira sala".
    if (this.route.snapshot.queryParamMap.get('nova') === '1') {
      this.formVisible.set(true);
      void this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.formVisible.set(true);
  }

  protected openEdit(room: TeacherRoomSummary): void {
    this.editing.set(room);
    this.formVisible.set(true);
  }

  protected async onSaved(): Promise<void> {
    await this.state.refresh();
  }

  protected async archive(room: TeacherRoomSummary): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Arquivar "${room.name}"?`,
      message:
        'A sala ficará somente leitura: alunos não poderão entrar nem fazer novas tentativas. O histórico é preservado e você continua vendo os relatórios.',
      acceptLabel: 'Arquivar',
    });

    if (confirmed) {
      await this.run(room.id, () => this.rooms.archive(room.id), 'Sala arquivada');
    }
  }

  protected async duplicate(room: TeacherRoomSummary): Promise<void> {
    const confirmed = await this.notify.confirm({
      header: `Duplicar "${room.name}"?`,
      message:
        'A cópia leva nome, série, temas, nota mínima e a trilha de lições. Alunos e progresso **não** são copiados, e a nova sala terá um código próprio.',
      acceptLabel: 'Duplicar',
      icon: 'pi pi-copy',
    });

    if (confirmed) {
      await this.run(room.id, () => this.rooms.duplicate(room.id), 'Sala duplicada');
    }
  }

  protected async regenerateCode(room: TeacherRoomSummary): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: 'Gerar um código novo?',
      message:
        'O código atual deixará de funcionar imediatamente. Alunos já matriculados continuam na sala; só quem ainda não entrou precisará do código novo.',
      acceptLabel: 'Gerar novo código',
    });

    if (confirmed) {
      await this.run(room.id, () => this.rooms.regenerateCode(room.id), 'Código regenerado');
    }
  }

  protected async remove(room: TeacherRoomSummary): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Excluir "${room.name}"?`,
      message:
        'Esta ação não pode ser desfeita. Só é possível excluir salas que nunca receberam alunos nem tentativas.',
      acceptLabel: 'Excluir',
    });

    if (confirmed) {
      await this.run(room.id, () => this.rooms.remove(room.id), 'Sala excluída');
    }
  }

  private async run(roomId: string, action: () => Promise<unknown>, success: string): Promise<void> {
    await this.actionGuard.run(async () => {
      this.busyRoomId.set(roomId);
      try {
        await action();
        this.notify.success(success);
        await this.state.refresh();
      } catch (error) {
        this.handleFailure(error);
      } finally {
        this.busyRoomId.set(null);
      }
    });
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível concluir a ação.');
      return;
    }

    // `409` na exclusão significa que a sala já foi usada — a saída é arquivar.
    if (error.status === 409) {
      this.notify.warn(
        'Esta sala já foi usada',
        'Salas com alunos ou tentativas não podem ser excluídas. Arquive-a para deixá-la somente leitura.',
      );
      return;
    }

    this.notify.error(error);
  }
}
