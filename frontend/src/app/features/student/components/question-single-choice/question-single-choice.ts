import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import type { AnswerPayload, AttemptQuestion } from '../../models/attempt-question';

/**
 * Questão de escolha única — Parte 4, §6.5.
 *
 * Importa **apenas** `attempt-question.ts`. O tipo `AttemptOption` não tem
 * campo `correct`, então não existe gabarito para vazar nem por engano: o
 * componente literalmente não tem acesso a essa informação.
 *
 * O enunciado das alternativas passa por `cc-markdown` porque questões de
 * matemática financeira usam KaTeX (`$20\\%$`).
 */
@Component({
  selector: 'cc-question-single-choice',
  imports: [MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="options">
      <legend class="cc-sr-only">Escolha uma alternativa</legend>

      @for (option of options(); track option.id; let i = $index) {
        <label class="option" [class.is-selected]="selected() === option.id" [class.is-locked]="disabled()">
          <input
            type="radio"
            class="cc-sr-only"
            [name]="groupName()"
            [value]="option.id"
            [checked]="selected() === option.id"
            [disabled]="disabled()"
            (change)="select(option.id)"
          />
          <span class="option__marker" aria-hidden="true">{{ letter(i) }}</span>
          <cc-markdown class="option__text" [content]="option.text" [compact]="true" />
        </label>
      }
    </fieldset>
  `,
  styleUrl: './question-choice.scss',
})
export class QuestionSingleChoiceComponent {
  readonly question = input.required<AttemptQuestion>();
  readonly disabled = input(false);
  /** Resposta já registrada, para reexibir em modo leitura. */
  readonly initial = input<AnswerPayload | null>(null);

  /** `null` enquanto nada foi escolhido — o botão de confirmar fica desativado. */
  readonly valueChange = output<AnswerPayload | null>();

  protected readonly selected = signal<string | null>(null);

  protected readonly options = computed(() => this.question().options ?? []);
  protected readonly groupName = computed(() => `q-${this.question().questionSnapshotId}`);

  constructor() {
    effect(() => {
      // Troca de questão ou hidratação: parte do que já estava registrado.
      const initial = this.initial();
      this.selected.set(initial?.selectedOptionIds?.[0] ?? null);
    });
  }

  protected select(optionId: string): void {
    if (this.disabled()) {
      return;
    }
    this.selected.set(optionId);
    this.valueChange.emit({ selectedOptionIds: [optionId] });
  }

  protected letter(index: number): string {
    return String.fromCharCode(65 + index);
  }
}
