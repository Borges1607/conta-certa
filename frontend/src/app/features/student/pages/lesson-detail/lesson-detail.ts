import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';

import { formatMinutes } from '../../../../core/util/format';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import { PageHeaderComponent, type Crumb } from '../../../../shared/components/page-header/page-header';
import { SecureFileLinkComponent } from '../../../../shared/components/secure-file-link/secure-file-link';
import { StarRatingComponent } from '../../../../shared/components/star-rating/star-rating';
import { createPageState } from '../../../../shared/forms/page-state';
import { DateTimePipe, FileSizePipe } from '../../../../shared/pipes/format.pipes';
import { LockReasonComponent } from '../../components/lock-reason/lock-reason';
import { AttemptLauncher } from '../../data/attempt-launcher';
import { RoomContextStore } from '../../data/room-context.store';
import { StudentLessonService } from '../../data/student-lesson.service';
import { StudentMediaService } from '../../data/student-media.service';
import type { AttemptHistoryItem, LessonDetail } from '../../models/lesson-track';

interface LessonPageData {
  lesson: LessonDetail;
  attempts: AttemptHistoryItem[];
}

/**
 * Lição: teoria, materiais e histórico — Parte 4, §5.2.
 *
 * A teoria passa por `cc-markdown`, que sanitiza e renderiza KaTeX. As regras
 * da tentativa aparecem **antes** de começar, e "sem limite" é escrito por
 * extenso quando a atribuição não tem limite — nunca um número inventado.
 */
@Component({
  selector: 'cc-lesson-detail-page',
  imports: [
    RouterLink,
    Button,
    Tag,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    MarkdownComponent,
    StarRatingComponent,
    SecureFileLinkComponent,
    LockReasonComponent,
    DateTimePipe,
    FileSizePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lesson-detail.html',
  styleUrl: './lesson-detail.scss',
})
export class LessonDetailPage {
  private readonly lessons = inject(StudentLessonService);
  private readonly media = inject(StudentMediaService);
  private readonly launcher = inject(AttemptLauncher);
  private readonly context = inject(RoomContextStore);

  readonly roomId = input.required<string>();
  readonly lessonId = input.required<string>();

  protected readonly starting = this.launcher.starting;

  protected readonly state = createPageState<LessonPageData>(async () => {
    const [lesson, attempts] = await Promise.all([
      this.lessons.detail(this.roomId(), this.lessonId()),
      this.lessons.attempts(this.roomId(), this.lessonId()),
    ]);
    return { lesson, attempts };
  });

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: this.context.currentRoomName(), link: `/aluno/salas/${this.roomId()}` },
    { label: 'Trilha', link: `/aluno/salas/${this.roomId()}/trilha` },
    { label: this.state.data()?.lesson.title ?? 'Lição' },
  ]);

  protected readonly isLocked = computed(
    () => this.state.data()?.lesson.availability === 'LOCKED',
  );

  constructor() {
    effect(() => {
      this.roomId();
      this.lessonId();
      void this.state.load();
    });
  }

  protected timeLabel(minutes: number | null): string {
    return formatMinutes(minutes);
  }

  /** Tentativas restantes por extenso — `null` significa sem limite. */
  protected attemptsLabel(lesson: LessonDetail): string {
    const { attemptsUsed, attemptsRemaining, maxAttempts } = lesson.rules;
    if (maxAttempts === null || attemptsRemaining === null) {
      return `${attemptsUsed} feitas · sem limite`;
    }
    return `${attemptsRemaining} de ${maxAttempts} restantes`;
  }

  protected async start(lesson: LessonDetail): Promise<void> {
    await this.launcher.start({
      assignmentId: lesson.assignmentId,
      title: lesson.title,
      rules: lesson.rules,
      activeAttemptId: lesson.activeAttemptId,
    });
  }

  /** Abrir um material registra a visualização, sem bloquear a abertura. */
  protected async openMaterial(materialId: string): Promise<void> {
    await this.media.registerView('MATERIAL', materialId);
  }
}
