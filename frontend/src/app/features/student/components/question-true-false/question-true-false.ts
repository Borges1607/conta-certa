import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';

import type { AnswerPayload, AttemptQuestion } from '../../models/attempt-question';

/**
 * Questão de verdadeiro ou falso — Parte 4, §6.5.
 *
 * Dois botões grandes em vez de um alternador: o aluno precisa **escolher**,
 * e um alternador nasceria com um valor já selecionado que ele não escolheu.
 */
@Component({
  selector: 'cc-question-true-false',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="tf">
      <legend class="cc-sr-only">Verdadeiro ou falso</legend>

      <label class="tf__option tf__option--true" [class.is-selected]="value() === true" [class.is-locked]="disabled()">
        <input
          type="radio"
          class="cc-sr-only"
          [name]="'tf-' + question().questionSnapshotId"
          [checked]="value() === true"
          [disabled]="disabled()"
          (change)="select(true)"
        />
        <i class="pi pi-check-circle" aria-hidden="true"></i>
        <span>Verdadeiro</span>
      </label>

      <label class="tf__option tf__option--false" [class.is-selected]="value() === false" [class.is-locked]="disabled()">
        <input
          type="radio"
          class="cc-sr-only"
          [name]="'tf-' + question().questionSnapshotId"
          [checked]="value() === false"
          [disabled]="disabled()"
          (change)="select(false)"
        />
        <i class="pi pi-times-circle" aria-hidden="true"></i>
        <span>Falso</span>
      </label>
    </fieldset>
  `,
  styleUrl: './question-true-false.scss',
})
export class QuestionTrueFalseComponent {
  readonly question = input.required<AttemptQuestion>();
  readonly disabled = input(false);
  readonly initial = input<AnswerPayload | null>(null);

  readonly valueChange = output<AnswerPayload | null>();

  protected readonly value = signal<boolean | null>(null);

  constructor() {
    effect(() => {
      const initial = this.initial();
      this.value.set(initial?.booleanValue ?? null);
    });
  }

  protected select(choice: boolean): void {
    if (this.disabled()) {
      return;
    }
    this.value.set(choice);
    this.valueChange.emit({ booleanValue: choice });
  }
}
