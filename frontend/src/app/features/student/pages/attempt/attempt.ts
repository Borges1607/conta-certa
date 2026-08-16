import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Button } from 'primeng/button';

import { ConnectionService } from '../../../../shared/layout/connection.service';
import { CountdownComponent } from '../../../../shared/components/countdown/countdown';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import { QuestionMultipleChoiceComponent } from '../../components/question-multiple-choice/question-multiple-choice';
import { QuestionNavigatorComponent } from '../../components/question-navigator/question-navigator';
import { QuestionNumericComponent } from '../../components/question-numeric/question-numeric';
import { QuestionSingleChoiceComponent } from '../../components/question-single-choice/question-single-choice';
import { QuestionTrueFalseComponent } from '../../components/question-true-false/question-true-false';
import { AttemptStore } from '../../data/attempt.store';
import type { AnswerPayload } from '../../models/attempt-question';

/**
 * Tentativa em andamento — Parte 4, §6.
 *
 * Esta tela importa **apenas** `attempt-question.ts`. Nada aqui — nem no
 * `AttemptStore`, nem nos componentes de questão — tem acesso a `correct`,
 * `correctAnswer` ou `explanation`. O sigilo do gabarito não depende de
 * disciplina: o tipo simplesmente não carrega esses campos.
 *
 * O cronômetro é do `cc-countdown`, que deriva tudo de `expiresAt` e do
 * `ServerClock`. Esta página não conhece o relógio local.
 */
@Component({
  selector: 'cc-attempt-page',
  imports: [
    Button,
    CountdownComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    MarkdownComponent,
    QuestionNavigatorComponent,
    QuestionSingleChoiceComponent,
    QuestionMultipleChoiceComponent,
    QuestionTrueFalseComponent,
    QuestionNumericComponent,
  ],
  providers: [AttemptStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './attempt.html',
  styleUrl: './attempt.scss',
})
export class AttemptPage {
  protected readonly store = inject(AttemptStore);
  protected readonly connection = inject(ConnectionService);

  readonly attemptId = input.required<string>();

  /** Rascunho da questão atual; `null` enquanto a resposta está incompleta. */
  protected readonly draft = signal<AnswerPayload | null>(null);

  protected readonly answeredIds = computed(
    () => new Set(this.store.answered().keys()) as ReadonlySet<string>,
  );

  protected readonly canConfirm = computed(
    () =>
      this.draft() !== null &&
      !this.store.currentIsAnswered() &&
      !this.store.answering() &&
      this.connection.isOnline(),
  );

  constructor() {
    effect(() => {
      void this.store.init(this.attemptId());
    });

    // Trocar de questão descarta o rascunho da anterior: ele nunca é enviado
    // para a questão errada.
    effect(() => {
      this.store.currentIndex();
      this.draft.set(null);
    });
  }

  /**
   * Guard de saída — Parte 4, §6.5.
   *
   * Sair não pausa nada: o tempo continua correndo no servidor, e o aviso do
   * store diz exatamente isso.
   */
  canDeactivate(): Promise<boolean> {
    return this.store.canLeave();
  }

  protected onDraft(payload: AnswerPayload | null): void {
    this.draft.set(payload);
  }

  protected async confirm(): Promise<void> {
    const payload = this.draft();
    if (payload) {
      await this.store.confirmCurrent(payload);
    }
  }
}
