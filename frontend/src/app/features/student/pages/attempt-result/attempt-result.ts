import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';

import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import { StarRatingComponent } from '../../../../shared/components/star-rating/star-rating';
import { createPageState } from '../../../../shared/forms/page-state';
import { DateTimePipe } from '../../../../shared/pipes/format.pipes';
import { AttemptService } from '../../data/attempt.service';
import { describeAnswer } from '../../models/answer-format';
import type { ResultAnswer } from '../../models/attempt-result';

/**
 * Resultado da tentativa — Parte 4, §6.6.
 *
 * Esta é a única tela do aluno que importa `attempt-result.ts` e, portanto, a
 * única que enxerga gabarito e explicação.
 *
 * **Nada aqui é recalculado.** `scorePercent`, `passed`, `stars`,
 * `xpEarnedThisAttempt` e `roomXpTotal` são exibidos como vieram. As faixas de
 * estrelas e a regra de XP aparecem como *texto explicativo* ao aluno — não
 * como fórmula executada (§11 da spec de integração).
 */
@Component({
  selector: 'cc-attempt-result-page',
  imports: [
    RouterLink,
    Button,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    StarRatingComponent,
    MarkdownComponent,
    DateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './attempt-result.html',
  styleUrl: './attempt-result.scss',
})
export class AttemptResultPage {
  private readonly attempts = inject(AttemptService);

  readonly attemptId = input.required<string>();
  /** `?expirado=1` — a tentativa foi encerrada pelo servidor, não pelo aluno. */
  readonly expirado = input<string | undefined>(undefined);

  protected readonly state = createPageState(() => this.attempts.result(this.attemptId()));

  protected readonly expiredByTime = computed(
    () => this.expirado() === '1' || this.state.data()?.status === 'EXPIRED',
  );

  constructor() {
    effect(() => {
      void this.state.load();
    });
  }

  /** Descrição textual de uma resposta, resolvendo ids de alternativa. */
  protected describe(answer: ResultAnswer, which: 'student' | 'correct'): string {
    return describeAnswer(
      answer.question,
      which === 'student' ? answer.studentAnswer : answer.correctAnswer,
    );
  }
}
