import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import type { AttemptQuestion } from '../../models/attempt-question';

/**
 * Navegador de questões — Parte 4, §6.5.
 *
 * Questões respondidas aparecem marcadas como **respondidas**, jamais como
 * certas ou erradas. Esse é o ponto mais sutil do sigilo do gabarito: seria
 * fácil colorir de verde e vermelho aqui, e isso vazaria a correção antes do
 * fim da tentativa. O componente nem recebe essa informação — só o conjunto de
 * ids já respondidos.
 */
@Component({
  selector: 'cc-question-navigator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="nav" aria-label="Questões da tentativa">
      <ol>
        @for (question of questions(); track question.questionSnapshotId; let i = $index) {
          <li>
            <button
              type="button"
              class="nav__item"
              [class.is-current]="i === currentIndex()"
              [class.is-answered]="answeredIds().has(question.questionSnapshotId)"
              [attr.aria-current]="i === currentIndex() ? 'step' : null"
              [attr.aria-label]="ariaLabel(i, answeredIds().has(question.questionSnapshotId))"
              (click)="questionSelected.emit(i)"
            >
              {{ i + 1 }}
            </button>
          </li>
        }
      </ol>
    </nav>
  `,
  styleUrl: './question-navigator.scss',
})
export class QuestionNavigatorComponent {
  readonly questions = input.required<readonly AttemptQuestion[]>();
  readonly currentIndex = input.required<number>();
  /** Somente os ids já respondidos — nenhuma informação de correção. */
  readonly answeredIds = input.required<ReadonlySet<string>>();

  readonly questionSelected = output<number>();

  protected ariaLabel(index: number, answered: boolean): string {
    return `Questão ${index + 1}${answered ? ', respondida' : ', sem resposta'}`;
  }
}
