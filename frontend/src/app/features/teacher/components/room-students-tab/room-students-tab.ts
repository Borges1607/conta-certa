import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';

import { ApiError } from '../../../../core/api/problem-details';
import type { PageQuery } from '../../../../core/models/page';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { StatusTagComponent } from '../../../../shared/components/status-tag/status-tag';
import { createPageState } from '../../../../shared/forms/page-state';
import { RelativeTimePipe } from '../../../../shared/pipes/format.pipes';
import { TeacherStudentService } from '../../data/teacher-student.service';
import type { RoomStudent } from '../../models/student';

/**
 * Aba de alunos da sala — Parte 5, §5.
 *
 * Remover aluno **preserva o histórico**, e a confirmação diz isso: o professor
 * precisa saber que a ação é reversível pelo código de ingresso, senão hesita
 * na hora errada.
 */
@Component({
  selector: 'cc-room-students-tab',
  imports: [
    FormsModule,
    Button,
    InputText,
    TableModule,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    StatusTagComponent,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-students-tab.html',
  styleUrl: './room-students-tab.scss',
})
export class RoomStudentsTabComponent {
  private readonly students = inject(TeacherStudentService);
  private readonly notify = inject(NotificationService);

  readonly roomId = input.required<string>();
  readonly archived = input(false);

  protected readonly search = signal('');
  protected readonly query = signal<PageQuery>({ page: 0, size: 20 });
  protected readonly busyId = signal<string | null>(null);

  private readonly guard = createSubmitGuard();

  protected readonly state = createPageState(() =>
    this.students.list(this.roomId(), this.query(), this.search()),
  );

  constructor() {
    effect(() => {
      this.roomId();
      this.query();
      void this.state.load();
    });
  }

  protected applySearch(value: string): void {
    this.search.set(value);
    this.query.set({ page: 0, size: 20 });
  }

  protected async remove(student: RoomStudent): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Remover ${student.fullName} da sala?`,
      message:
        'O histórico do aluno é preservado: notas, tentativas e XP continuam guardados. Se ele entrar de novo com o código da sala, tudo volta exatamente como estava.',
      acceptLabel: 'Remover da sala',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.busyId.set(student.studentId);
      try {
        await this.students.remove(this.roomId(), student.studentId);
        this.notify.success('Aluno removido', 'O histórico foi preservado.');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível remover o aluno.');
      } finally {
        this.busyId.set(null);
      }
    });
  }
}
