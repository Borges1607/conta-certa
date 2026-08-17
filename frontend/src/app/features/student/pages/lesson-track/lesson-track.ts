import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';

import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { ProgressBarComponent } from '../../../../shared/components/progress-bar/progress-bar';
import { createPageState } from '../../../../shared/forms/page-state';
import { LessonPathNodeComponent } from '../../components/lesson-path-node/lesson-path-node';
import { AttemptLauncher } from '../../data/attempt-launcher';
import { RoomContextStore } from '../../data/room-context.store';
import { StudentLessonService } from '../../data/student-lesson.service';
import type { LessonTrackItem } from '../../models/lesson-track';

/**
 * Trilha da sala — Parte 4, §5.1.
 *
 * A lista chega ordenada e com o estado de cada item decidido pela API. Esta
 * página **não reordena, não filtra e não desbloqueia nada**: ela reflete a
 * resposta. É o que cumpre "a próxima lição só libera após aprovação da
 * anterior" sem que o frontend precise conhecer essa regra.
 */
@Component({
  selector: 'cc-lesson-track-page',
  imports: [
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    ProgressBarComponent,
    LessonPathNodeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lesson-track.html',
  styleUrl: './lesson-track.scss',
})
export class LessonTrackPage {
  private readonly lessons = inject(StudentLessonService);
  private readonly launcher = inject(AttemptLauncher);

  protected readonly context = inject(RoomContextStore);

  /** Ligado pelo router (`withComponentInputBinding`). */
  readonly roomId = input.required<string>();

  protected readonly starting = this.launcher.starting;

  protected readonly state = createPageState<LessonTrackItem[]>(() =>
    this.lessons.track(this.roomId()),
  );

  /** Progresso da sala — vem do dashboard, não é contado aqui. */
  protected readonly progressPercent = computed(
    () => this.context.dashboard.data()?.progressPercent ?? null,
  );

  constructor() {
    // Trocar de sala é uma navegação: o `roomId` muda e a trilha recarrega.
    effect(() => {
      this.roomId();
      void this.state.load();
    });
  }

  protected async start(item: LessonTrackItem): Promise<void> {
    await this.launcher.start({
      assignmentId: item.assignmentId,
      title: item.title,
      rules: item.rules,
      activeAttemptId: item.activeAttemptId,
    });
  }

  protected async resume(attemptId: string): Promise<void> {
    await this.launcher.resume(attemptId);
  }
}
