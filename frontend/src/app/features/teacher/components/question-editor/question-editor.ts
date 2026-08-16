import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { FormsModule } from '@angular/forms';

import { ApiError } from '../../../../core/api/problem-details';
import type { QuestionType } from '../../../../core/models/enums';
import { QUESTION_TYPE_LABELS } from '../../../../core/models/labels';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { QuestionService } from '../../data/question.service';
import type {
  CreateQuestionRequest,
  PatchQuestionRequest,
  Question,
  QuestionOption,
  QuestionPayload,
} from '../../models/question';
import { MarkdownEditorComponent } from '../markdown-editor/markdown-editor';
import {
  QuestionTypeFieldsComponent,
  type NumericAnswer,
} from '../question-type-fields/question-type-fields';
import { VersionConflictNoticeComponent } from '../version-conflict-notice/version-conflict-notice';

interface TypeOption {
  value: QuestionType;
  label: string;
}

const EMPTY_NUMERIC: NumericAnswer = {
  correctNumericValue: '',
  absoluteTolerance: '0',
  unit: 'NONE',
  decimalPlaces: 2,
};

/**
 * Editor de questão — Parte 5, §6.3.
 *
 * Valida **antes de enviar**, além do servidor: uma escolha única com zero ou
 * duas corretas nem chega a virar requisição. As mensagens explicam a regra,
 * não só o fato de estar inválido.
 */
@Component({
  selector: 'cc-question-editor',
  imports: [
    FormsModule,
    Dialog,
    Button,
    Select,
    SubmitButtonComponent,
    MarkdownEditorComponent,
    QuestionTypeFieldsComponent,
    VersionConflictNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './question-editor.html',
  styleUrl: './question-editor.scss',
})
export class QuestionEditorComponent {
  private readonly questions = inject(QuestionService);
  private readonly notify = inject(NotificationService);

  readonly visible = model(false);
  readonly lessonId = input.required<string>();
  /** `null` cria; preenchido edita. */
  readonly question = input<Question | null>(null);
  readonly readOnly = input(false);

  readonly saved = output<Question>();

  protected readonly guard = createSubmitGuard();
  protected readonly conflict = signal(false);
  protected readonly reloading = signal(false);
  protected readonly serverErrors = signal<string[]>([]);
  protected readonly showLocalErrors = signal(false);

  protected readonly prompt = signal('');
  protected readonly explanation = signal('');
  protected readonly type = signal<QuestionType>('SINGLE_CHOICE');
  protected readonly options = signal<QuestionOption[]>([]);
  protected readonly correctBoolean = signal<boolean | null>(null);
  protected readonly numeric = signal<NumericAnswer>({ ...EMPTY_NUMERIC });

  protected readonly typeOptions: TypeOption[] = (
    Object.keys(QUESTION_TYPE_LABELS) as QuestionType[]
  ).map((value) => ({ value, label: QUESTION_TYPE_LABELS[value] }));

  protected readonly isEdit = computed(() => this.question() !== null);

  /** Regras locais da Parte 5, §6.3. Vazio significa "pode enviar". */
  protected readonly localErrors = computed<string[]>(() => {
    const errors: string[] = [];

    if (this.prompt().trim().length === 0) {
      errors.push('O enunciado é obrigatório.');
    }

    switch (this.type()) {
      case 'SINGLE_CHOICE': {
        const filled = this.filledOptions();
        const correct = filled.filter((option) => option.correct).length;
        if (filled.length < 2) {
          errors.push('Escolha única exige duas ou mais opções com texto.');
        }
        if (correct !== 1) {
          errors.push(
            `Escolha única exige exatamente uma opção correta — há ${correct} marcada(s).`,
          );
        }
        break;
      }
      case 'MULTIPLE_CHOICE': {
        const filled = this.filledOptions();
        const correct = filled.filter((option) => option.correct).length;
        if (filled.length < 2) {
          errors.push('Múltipla escolha exige duas ou mais opções com texto.');
        }
        if (correct < 2) {
          errors.push('Múltipla escolha exige duas ou mais opções corretas.');
        }
        break;
      }
      case 'TRUE_FALSE':
        if (this.correctBoolean() === null) {
          errors.push('Escolha se a afirmação é verdadeira ou falsa.');
        }
        break;
      case 'NUMERIC': {
        const value = this.numeric().correctNumericValue.trim();
        if (value.length === 0) {
          errors.push('Informe o valor correto.');
        } else if (!/^-?\d+([.,]\d+)?$/.test(value)) {
          errors.push('O valor correto deve ser um número decimal, como 1250.50.');
        }
        break;
      }
    }

    return errors;
  });

  protected readonly canSubmit = computed(
    () => this.localErrors().length === 0 && !this.conflict() && !this.readOnly(),
  );

  constructor() {
    effect(() => {
      if (this.visible()) {
        this.reset();
      }
    });
  }

  protected async submit(): Promise<void> {
    this.showLocalErrors.set(true);
    if (!this.canSubmit()) {
      return;
    }

    this.serverErrors.set([]);

    await this.guard.run(async () => {
      try {
        const current = this.question();
        const result = current
          ? await this.questions.update(current.id, this.patchBody(current.version))
          : await this.questions.create(this.lessonId(), this.createBody());

        this.notify.success(current ? 'Questão atualizada' : 'Questão criada');
        this.saved.emit(result);
        this.visible.set(false);
      } catch (error) {
        this.handleError(error);
      }
    });
  }

  protected async reload(): Promise<void> {
    const current = this.question();
    if (!current || this.reloading()) {
      return;
    }

    this.reloading.set(true);
    try {
      const fresh = (await this.questions.list(this.lessonId())).find(
        (item) => item.id === current.id,
      );
      if (fresh) {
        this.saved.emit(fresh);
        this.applyValues(fresh);
      }
      this.conflict.set(false);
      this.notify.info('Dados recarregados', 'O editor agora mostra a versão mais recente.');
    } catch (error) {
      this.notify.error(
        error instanceof ApiError ? error : 'Não foi possível recarregar a questão.',
      );
    } finally {
      this.reloading.set(false);
    }
  }

  protected close(): void {
    this.visible.set(false);
  }

  protected onTypeChange(type: QuestionType): void {
    this.type.set(type);
    // Trocar de tipo não pode carregar gabarito do tipo anterior.
    if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE') {
      if (this.options().length === 0) {
        this.options.set([
          { id: null, text: '', correct: false },
          { id: null, text: '', correct: false },
        ]);
      }
    } else if (type === 'TRUE_FALSE') {
      this.correctBoolean.set(this.correctBoolean() ?? null);
    }
  }

  private filledOptions(): QuestionOption[] {
    return this.options().filter((option) => option.text.trim().length > 0);
  }

  private reset(): void {
    this.conflict.set(false);
    this.serverErrors.set([]);
    this.showLocalErrors.set(false);

    const current = this.question();
    if (current) {
      this.applyValues(current);
      return;
    }

    this.prompt.set('');
    this.explanation.set('');
    this.type.set('SINGLE_CHOICE');
    this.options.set([
      { id: null, text: '', correct: false },
      { id: null, text: '', correct: false },
    ]);
    this.correctBoolean.set(null);
    this.numeric.set({ ...EMPTY_NUMERIC });
  }

  private applyValues(question: Question): void {
    this.prompt.set(question.prompt);
    this.explanation.set(question.explanation ?? '');
    this.type.set(question.type);
    this.options.set(question.options.map((option) => ({ ...option })));
    this.correctBoolean.set(question.correctBoolean);
    this.numeric.set({
      correctNumericValue: question.correctNumericValue ?? '',
      absoluteTolerance: question.absoluteTolerance ?? '0',
      unit: question.unit ?? 'NONE',
      decimalPlaces: question.decimalPlaces,
    });
  }

  private payload(): QuestionPayload {
    const type = this.type();
    const base: QuestionPayload = {
      prompt: this.prompt().trim(),
      type,
      explanation: this.explanation().trim() || null,
    };

    if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE') {
      return {
        ...base,
        options: this.filledOptions().map((option) => ({
          id: option.id,
          text: option.text.trim(),
          correct: option.correct,
        })),
      };
    }

    if (type === 'TRUE_FALSE') {
      return { ...base, correctBoolean: this.correctBoolean() };
    }

    const numeric = this.numeric();
    return {
      ...base,
      // String decimal, sempre: o valor nunca vira `number` no caminho.
      correctNumericValue: numeric.correctNumericValue.trim().replace(',', '.'),
      absoluteTolerance: numeric.absoluteTolerance.trim().replace(',', '.') || '0',
      unit: numeric.unit,
      decimalPlaces: numeric.decimalPlaces,
    };
  }

  private createBody(): CreateQuestionRequest {
    return this.payload();
  }

  private patchBody(version: number): PatchQuestionRequest {
    return { version, ...this.payload() };
  }

  private handleError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível salvar a questão.');
      return;
    }

    if (error.isVersionConflict) {
      this.conflict.set(true);
      return;
    }

    if (error.isValidation) {
      this.serverErrors.set(
        error.fieldErrors.length > 0
          ? error.fieldErrors.map((item) => `${item.field}: ${item.message}`)
          : [error.detail],
      );
      return;
    }

    this.notify.error(error);
  }
}
