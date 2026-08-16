import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Button } from 'primeng/button';

import { formatMinutes } from '../../../../core/util/format';
import { StatusTagComponent } from '../../../../shared/components/status-tag/status-tag';
import { DateTimePipe } from '../../../../shared/pipes/format.pipes';
import { TIME_ZONE_LABEL } from '../../util/brasilia-time';
import type { LessonAssignment } from '../../models/assignment';

/**
 * Uma linha da trilha — Parte 5, §7.
 *
 * Mostra o resumo do que o aluno verá: janela de disponibilidade, tempo,
 * tentativas e número de questões. Onde a atribuição não limita, o texto diz
 * "sem limite" — nunca um número inventado pelo frontend.
 */
@Component({
  selector: 'cc-assignment-row',
  imports: [Button, StatusTagComponent, DateTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './assignment-row.html',
  styleUrl: './assignment-row.scss',
})
export class AssignmentRowComponent {
  readonly assignment = input.required<LessonAssignment>();
  readonly readOnly = input(false);
  readonly busy = input(false);
  readonly isFirst = input(false);
  readonly isLast = input(false);

  readonly editRequested = output<LessonAssignment>();
  readonly removeRequested = output<LessonAssignment>();
  readonly moveUpRequested = output<LessonAssignment>();
  readonly moveDownRequested = output<LessonAssignment>();

  protected readonly timeZoneLabel = TIME_ZONE_LABEL;

  protected readonly timeLabel = computed(() =>
    formatMinutes(this.assignment().timeLimitMinutes),
  );

  protected readonly attemptsLabel = computed(() => {
    const max = this.assignment().maxAttempts;
    return max === null ? 'sem limite' : `${max} ${max === 1 ? 'tentativa' : 'tentativas'}`;
  });

  protected readonly questionsLabel = computed(() => {
    const { questionCount, lesson } = this.assignment();
    return questionCount === null
      ? `todas (${lesson.activeQuestionCount})`
      : `${questionCount} de ${lesson.activeQuestionCount}`;
  });
}
