import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { Button } from 'primeng/button';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { createPageState } from '../../../../shared/forms/page-state';
import { AssignmentFormComponent } from '../assignment-form/assignment-form';
import { AssignmentRowComponent } from '../assignment-row/assignment-row';
import { LessonAssignmentService } from '../../data/lesson-assignment.service';
import type { LessonAssignment } from '../../models/assignment';

/**
 * Aba da trilha da sala — Parte 5, §7.
 *
 * É a tela que liga o acervo à sala. A ordem é a da trilha do aluno, e mover um
 * item é uma operação otimista **revertida se a API recusar** — deixar a tela
 * numa ordem que o servidor não tem seria pior que o pequeno atraso.
 */
@Component({
  selector: 'cc-room-track-tab',
  imports: [
    Button,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    AssignmentFormComponent,
    AssignmentRowComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-track-tab.html',
  styleUrl: './room-track-tab.scss',
})
export class RoomTrackTabComponent {
  private readonly assignments = inject(LessonAssignmentService);
  private readonly notify = inject(NotificationService);

  readonly roomId = input.required<string>();
  readonly archived = input(false);

  /** A trilha mudou: o detalhe da sala recarrega a contagem de lições. */
  readonly changed = output<void>();

  protected readonly state = createPageState(() => this.assignments.list(this.roomId()));

  protected readonly formVisible = signal(false);
  protected readonly editing = signal<LessonAssignment | null>(null);
  protected readonly busyId = signal<string | null>(null);

  private readonly guard = createSubmitGuard();

  constructor() {
    effect(() => {
      this.roomId();
      void this.state.load();
    });
  }

  protected nextPosition(): number {
    return (this.state.data()?.length ?? 0) + 1;
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.formVisible.set(true);
  }

  protected openEdit(assignment: LessonAssignment): void {
    this.editing.set(assignment);
    this.formVisible.set(true);
  }

  protected async onSaved(): Promise<void> {
    await this.state.refresh();
    this.changed.emit();
  }

  /** Move um item e persiste a nova ordem. */
  protected async move(assignment: LessonAssignment, direction: -1 | 1): Promise<void> {
    const current = this.state.data();
    if (!current) {
      return;
    }

    const index = current.findIndex((item) => item.id === assignment.id);
    const target = index + direction;
    if (index === -1 || target < 0 || target >= current.length) {
      return;
    }

    const reordered = [...current];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];

    // Otimista: a lista muda na hora e volta ao original se a API recusar.
    this.state.set(reordered);

    await this.guard.run(async () => {
      try {
        const saved = await this.assignments.reorder(this.roomId(), {
          assignmentIds: reordered.map((item) => item.id),
        });
        this.state.set(saved);
      } catch (error) {
        this.state.set(current);
        this.notify.error(
          error instanceof ApiError ? error : 'Não foi possível reordenar a trilha.',
          'Ordem não salva',
        );
      }
    });
  }

  protected async remove(assignment: LessonAssignment): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Retirar "${assignment.lesson.title}" da trilha?`,
      message:
        'A lição continua no seu acervo e pode ser atribuída de novo. Só atribuições que ainda não foram usadas podem ser retiradas.',
      acceptLabel: 'Retirar da trilha',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.busyId.set(assignment.id);
      try {
        await this.assignments.remove(this.roomId(), assignment.id);
        this.notify.success('Lição retirada da trilha');
        await this.state.refresh();
        this.changed.emit();
      } catch (error) {
        this.handleRemoveFailure(error);
      } finally {
        this.busyId.set(null);
      }
    });
  }

  private handleRemoveFailure(error: unknown): void {
    if (error instanceof ApiError && error.status === 409) {
      // Trilha em uso não se desmonta: o caminho é arquivar a atribuição.
      this.notify.warn(
        'Esta lição já está em uso',
        'Alunos já iniciaram tentativas nela. Em vez de retirar, mude a situação para "Arquivado" na configuração da atribuição.',
      );
      return;
    }

    this.notify.error(
      error instanceof ApiError ? error : 'Não foi possível retirar a lição da trilha.',
    );
  }
}
