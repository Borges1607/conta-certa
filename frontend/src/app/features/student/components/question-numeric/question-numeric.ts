import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

import type { AnswerPayload, AttemptQuestion } from '../../models/attempt-question';
import { normalizeDecimal, unitSuffix } from '../../models/answer-format';

/**
 * Questão numérica — Parte 4, §6.5.
 *
 * O valor enviado é **string decimal**, nunca `float` (§2.1 e §6.3 da spec de
 * integração). Por isso o campo é um `input type="text"` com `inputmode`
 * numérico, e não um `p-inputnumber`: um componente numérico devolveria
 * `number` e a precisão se perderia antes mesmo do envio.
 *
 * A normalização é feita por `normalizeDecimal`, que trabalha só com operações
 * de string — `"100,5"` com duas casas vira `"100.50"`, não `100.5`.
 */
@Component({
  selector: 'cc-question-numeric',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="numeric">
      <div class="numeric__field" [class.is-locked]="disabled()">
        @if (prefix()) {
          <span class="numeric__affix" aria-hidden="true">{{ prefix() }}</span>
        }

        <input
          #field
          type="text"
          inputmode="decimal"
          class="numeric__input"
          [id]="inputId()"
          [value]="raw()"
          [disabled]="disabled()"
          [attr.aria-label]="'Resposta numérica'"
          [attr.aria-describedby]="inputId() + '-help'"
          autocomplete="off"
          (input)="onInput(field.value)"
        />

        @if (suffix()) {
          <span class="numeric__affix" aria-hidden="true">{{ suffix() }}</span>
        }
      </div>

      <p class="numeric__help cc-small cc-muted" [id]="inputId() + '-help'">
        {{ helpText() }}
      </p>

      @if (raw() && !normalized()) {
        <p class="numeric__error" role="alert">Digite um número válido.</p>
      } @else if (normalized(); as value) {
        <p class="numeric__preview cc-xs cc-muted">Será enviado como <code>{{ value }}</code></p>
      }
    </div>
  `,
  styleUrl: './question-numeric.scss',
})
export class QuestionNumericComponent {
  readonly question = input.required<AttemptQuestion>();
  readonly disabled = input(false);
  readonly initial = input<AnswerPayload | null>(null);

  readonly valueChange = output<AnswerPayload | null>();

  protected readonly raw = signal('');

  protected readonly inputId = computed(() => `numeric-${this.question().questionSnapshotId}`);

  protected readonly decimalPlaces = computed(() => this.question().numeric?.decimalPlaces ?? 0);

  /** `null` enquanto o texto não for um número válido. */
  protected readonly normalized = computed(() =>
    normalizeDecimal(this.raw(), this.decimalPlaces()),
  );

  protected readonly prefix = computed(() =>
    this.question().numeric?.unit === 'BRL' ? 'R$' : '',
  );

  protected readonly suffix = computed(() =>
    this.question().numeric?.unit === 'PERCENT' ? '%' : '',
  );

  protected readonly helpText = computed(() => {
    const places = this.decimalPlaces();
    const unit = unitSuffix(this.question());
    const casas =
      places === 0
        ? 'Use um número inteiro.'
        : `Use ${places} ${places === 1 ? 'casa decimal' : 'casas decimais'}.`;
    return unit ? `${casas} Valor em ${unit === 'R$' ? 'reais' : 'porcentagem'}.` : casas;
  });

  constructor() {
    effect(() => {
      const initial = this.initial();
      this.raw.set(initial?.numericValue ?? '');
    });
  }

  protected onInput(value: string): void {
    if (this.disabled()) {
      return;
    }
    this.raw.set(value);

    const normalized = normalizeDecimal(value, this.decimalPlaces());
    this.valueChange.emit(normalized === null ? null : { numericValue: normalized });
  }
}
