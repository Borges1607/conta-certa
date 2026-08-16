import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { FormsModule } from '@angular/forms';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { RadioButton } from 'primeng/radiobutton';
import { Select } from 'primeng/select';

import type { NumericUnit, QuestionType } from '../../../../core/models/enums';
import { NUMERIC_UNIT_LABELS } from '../../../../core/models/labels';
import type { QuestionOption } from '../../models/question';

export interface NumericAnswer {
  /** String decimal — §2.1 da spec: valores nunca trafegam como `float`. */
  correctNumericValue: string;
  absoluteTolerance: string;
  unit: NumericUnit;
  decimalPlaces: number | null;
}

interface UnitOption {
  value: NumericUnit;
  label: string;
}

/**
 * Campos específicos por `QuestionType` — Parte 5, §6.3.
 *
 * O editor muda conforme o tipo. As regras locais que este componente sinaliza
 * são validadas **de novo** pelo servidor: elas existem para o professor não
 * perder o trabalho num `422` evitável, não para substituir a validação real.
 */
@Component({
  selector: 'cc-question-type-fields',
  imports: [
    FormsModule,
    Button,
    Checkbox,
    InputNumber,
    InputText,
    Message,
    RadioButton,
    Select,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './question-type-fields.html',
  styleUrl: './question-type-fields.scss',
})
export class QuestionTypeFieldsComponent {
  readonly type = input.required<QuestionType>();
  readonly disabled = input(false);

  readonly options = model<QuestionOption[]>([]);
  readonly correctBoolean = model<boolean | null>(null);
  readonly numeric = model<NumericAnswer>({
    correctNumericValue: '',
    absoluteTolerance: '0',
    unit: 'NONE',
    decimalPlaces: 2,
  });

  protected readonly unitOptions: UnitOption[] = (
    Object.keys(NUMERIC_UNIT_LABELS) as NumericUnit[]
  ).map((value) => ({ value, label: NUMERIC_UNIT_LABELS[value] }));

  protected readonly correctCount = computed(
    () => this.options().filter((option) => option.correct).length,
  );

  /** Índice da opção correta em `SINGLE_CHOICE`; `-1` quando nenhuma. */
  protected readonly correctIndex = computed(() =>
    this.options().findIndex((option) => option.correct),
  );

  protected addOption(): void {
    if (this.disabled()) {
      return;
    }
    this.options.set([...this.options(), { id: null, text: '', correct: false }]);
  }

  protected removeOption(index: number): void {
    if (this.disabled()) {
      return;
    }
    this.options.set(this.options().filter((_, i) => i !== index));
  }

  protected setOptionText(index: number, event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.options.set(
      this.options().map((option, i) => (i === index ? { ...option, text } : option)),
    );
  }

  /** Escolha única: marcar uma desmarca as demais. */
  protected setSingleCorrect(index: number): void {
    this.options.set(this.options().map((option, i) => ({ ...option, correct: i === index })));
  }

  protected toggleCorrect(index: number, correct: boolean): void {
    this.options.set(
      this.options().map((option, i) => (i === index ? { ...option, correct } : option)),
    );
  }

  protected moveOption(index: number, offset: number): void {
    const target = index + offset;
    const current = this.options();
    if (this.disabled() || target < 0 || target >= current.length) {
      return;
    }
    const next = [...current];
    const [moved] = next.splice(index, 1);
    next.splice(target, 0, moved);
    this.options.set(next);
  }

  protected setNumeric<K extends keyof NumericAnswer>(key: K, value: NumericAnswer[K]): void {
    this.numeric.set({ ...this.numeric(), [key]: value });
  }

  protected onNumericText(key: 'correctNumericValue' | 'absoluteTolerance', event: Event): void {
    this.setNumeric(key, (event.target as HTMLInputElement).value);
  }
}
