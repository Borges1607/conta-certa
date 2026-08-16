import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import type { AnswerPayload, AttemptQuestion } from '../../models/attempt-question';

/**
 * Questão de múltipla escolha — Parte 4, §6.5.
 *
 * Avisa que **só a seleção exata pontua** (§7.2 da spec de integração), porque
 * a diferença entre "acertei quase todas" e "errei" não é óbvia para o aluno.
 *
 * Como as demais questões em andamento, importa somente `attempt-question.ts`:
 * não existe gabarito acessível aqui.
 */
@Component({
  selector: 'cc-question-multiple-choice',
  imports: [MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="hint">
      <i class="pi pi-info-circle" aria-hidden="true"></i>
      Marque todas as alternativas corretas. Só a seleção exata pontua.
    </p>

    <fieldset class="options">
      <legend class="cc-sr-only">Escolha uma ou mais alternativas</legend>

      @for (option of options(); track option.id; let i = $index) {
        <label class="option" [class.is-selected]="isSelected(option.id)" [class.is-locked]="disabled()">
          <input
            type="checkbox"
            class="cc-sr-only"
            [checked]="isSelected(option.id)"
            [disabled]="disabled()"
            (change)="toggle(option.id)"
          />
          <span class="option__marker option__marker--square" aria-hidden="true">
            @if (isSelected(option.id)) {
              <i class="pi pi-check"></i>
            } @else {
              {{ letter(i) }}
            }
          </span>
          <cc-markdown class="option__text" [content]="option.text" [compact]="true" />
        </label>
      }
    </fieldset>
  `,
  styleUrls: ['../question-single-choice/question-choice.scss', './question-multiple-choice.scss'],
})
export class QuestionMultipleChoiceComponent {
  readonly question = input.required<AttemptQuestion>();
  readonly disabled = input(false);
  readonly initial = input<AnswerPayload | null>(null);

  readonly valueChange = output<AnswerPayload | null>();

  protected readonly selected = signal<readonly string[]>([]);

  protected readonly options = computed(() => this.question().options ?? []);

  constructor() {
    effect(() => {
      const initial = this.initial();
      this.selected.set(initial?.selectedOptionIds ?? []);
    });
  }

  protected isSelected(optionId: string): boolean {
    return this.selected().includes(optionId);
  }

  protected toggle(optionId: string): void {
    if (this.disabled()) {
      return;
    }

    const current = this.selected();
    const updated = current.includes(optionId)
      ? current.filter((id) => id !== optionId)
      : [...current, optionId];

    this.selected.set(updated);
    // Nenhuma alternativa marcada é resposta incompleta, não resposta vazia.
    this.valueChange.emit(updated.length > 0 ? { selectedOptionIds: [...updated] } : null);
  }

  protected letter(index: number): string {
    return String.fromCharCode(65 + index);
  }
}
