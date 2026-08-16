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
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';

import { ApiError } from '../../../../core/api/problem-details';
import type { ContentStatus } from '../../../../core/models/enums';
import { CONTENT_STATUS_LABELS } from '../../../../core/models/labels';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { DateTimePipe } from '../../../../shared/pipes/format.pipes';
import { applyFieldErrors, markAllTouched } from '../../../../shared/forms/apply-field-errors';
import { LessonAssignmentService } from '../../data/lesson-assignment.service';
import { LessonService } from '../../data/lesson.service';
import {
  DEFAULT_MAX_ATTEMPTS,
  DEFAULT_TIME_LIMIT_MINUTES,
  type CreateAssignmentRequest,
  type LessonAssignment,
  type PatchAssignmentRequest,
} from '../../models/assignment';
import type { LessonSummary } from '../../models/lesson';
import {
  TIME_ZONE_LABEL,
  instantToPickedDate,
  pickedDateToInstant,
} from '../../util/brasilia-time';
import { VersionConflictNoticeComponent } from '../version-conflict-notice/version-conflict-notice';

interface StatusOption {
  value: ContentStatus;
  label: string;
}

/**
 * Formulário de atribuição de lição — Parte 5, §7.
 *
 * Duas responsabilidades explícitas desta tela:
 *
 * 1. **Fuso.** O professor escolhe em horário de Brasília; a API recebe ISO
 *    8601 UTC. A conversão é feita em `pickedDateToInstant`, e cada campo
 *    mostra o fuso e o instante que será enviado.
 * 2. **Sem limite é `null`.** `timeLimitMinutes`, `maxAttempts` e
 *    `questionCount` viram `null` quando o alternador está ligado — nunca zero,
 *    nunca um número inventado.
 */
@Component({
  selector: 'cc-assignment-form',
  imports: [
    ReactiveFormsModule,
    // Os alternadores "sem limite" não são campos do formulário: eles decidem
    // se o campo vai como `null`. Por isso usam ngModel standalone.
    FormsModule,
    Dialog,
    Button,
    DatePicker,
    InputNumber,
    Select,
    ToggleSwitch,
    FormFieldComponent,
    SubmitButtonComponent,
    VersionConflictNoticeComponent,
    DateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './assignment-form.html',
  styleUrl: './assignment-form.scss',
})
export class AssignmentFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly assignments = inject(LessonAssignmentService);
  private readonly lessons = inject(LessonService);
  private readonly notify = inject(NotificationService);

  readonly visible = model(false);
  readonly roomId = input.required<string>();
  /** `null` cria uma nova atribuição; preenchido edita. */
  readonly assignment = input<LessonAssignment | null>(null);
  readonly nextPosition = input(1);

  readonly saved = output<LessonAssignment>();

  protected readonly timeZoneLabel = TIME_ZONE_LABEL;
  protected readonly guard = createSubmitGuard();
  protected readonly conflict = signal(false);
  protected readonly reloading = signal(false);
  protected readonly summaryErrors = signal<string[]>([]);
  protected readonly lessonOptions = signal<LessonSummary[]>([]);
  protected readonly lessonsLoading = signal(false);
  protected readonly lessonsError = signal<string>('');

  /** Alternadores "sem limite" — ligados significam `null` no envio. */
  protected readonly noTimeLimit = signal(false);
  protected readonly noAttemptLimit = signal(false);
  protected readonly allQuestions = signal(true);

  protected readonly statusOptions: StatusOption[] = (
    Object.keys(CONTENT_STATUS_LABELS) as ContentStatus[]
  ).map((value) => ({ value, label: CONTENT_STATUS_LABELS[value] }));

  protected readonly form = this.fb.group({
    lessonId: this.fb.control<string | null>(null, [Validators.required]),
    status: this.fb.nonNullable.control<ContentStatus>('DRAFT'),
    availableFrom: this.fb.control<Date | null>(null),
    dueAt: this.fb.control<Date | null>(null),
    timeLimitMinutes: this.fb.control<number | null>(DEFAULT_TIME_LIMIT_MINUTES, [
      Validators.min(1),
    ]),
    maxAttempts: this.fb.control<number | null>(DEFAULT_MAX_ATTEMPTS, [Validators.min(1)]),
    questionCount: this.fb.control<number | null>(null, [Validators.min(1)]),
    shuffleQuestions: this.fb.nonNullable.control(true),
    shuffleOptions: this.fb.nonNullable.control(true),
  });

  protected readonly isEdit = computed(() => this.assignment() !== null);

  /** Instantes que serão realmente enviados, em UTC — mostrados na tela. */
  protected readonly availableFromInstant = signal<string | null>(null);
  protected readonly dueAtInstant = signal<string | null>(null);

  protected readonly dateRangeInvalid = signal(false);

  constructor() {
    effect(() => {
      if (this.visible()) {
        this.reset();
        void this.loadLessons();
      }
    });
  }

  protected onDateChange(): void {
    const from = this.form.controls.availableFrom.value;
    const due = this.form.controls.dueAt.value;
    this.availableFromInstant.set(pickedDateToInstant(from));
    this.dueAtInstant.set(pickedDateToInstant(due));
    this.dateRangeInvalid.set(Boolean(from && due && due.getTime() <= from.getTime()));
  }

  protected toggleNoTimeLimit(value: boolean): void {
    this.noTimeLimit.set(value);
    if (value) {
      this.form.controls.timeLimitMinutes.setValue(null);
    } else if (this.form.controls.timeLimitMinutes.value === null) {
      this.form.controls.timeLimitMinutes.setValue(DEFAULT_TIME_LIMIT_MINUTES);
    }
  }

  protected toggleNoAttemptLimit(value: boolean): void {
    this.noAttemptLimit.set(value);
    if (value) {
      this.form.controls.maxAttempts.setValue(null);
    } else if (this.form.controls.maxAttempts.value === null) {
      this.form.controls.maxAttempts.setValue(DEFAULT_MAX_ATTEMPTS);
    }
  }

  protected toggleAllQuestions(value: boolean): void {
    this.allQuestions.set(value);
    if (value) {
      this.form.controls.questionCount.setValue(null);
    }
  }

  protected async submit(): Promise<void> {
    markAllTouched(this.form);
    this.onDateChange();

    if (this.form.invalid || this.dateRangeInvalid() || this.conflict()) {
      return;
    }

    this.summaryErrors.set([]);

    await this.guard.run(async () => {
      try {
        const current = this.assignment();
        const result = current
          ? await this.assignments.update(this.roomId(), current.id, this.patchBody(current.version))
          : await this.assignments.create(this.roomId(), this.createBody());

        this.notify.success(current ? 'Atribuição atualizada' : 'Lição adicionada à trilha');
        this.saved.emit(result);
        this.visible.set(false);
      } catch (error) {
        this.handleError(error);
      }
    });
  }

  protected async reload(): Promise<void> {
    const current = this.assignment();
    if (!current || this.reloading()) {
      return;
    }

    this.reloading.set(true);
    try {
      const fresh = (await this.assignments.list(this.roomId())).find(
        (item) => item.id === current.id,
      );
      if (fresh) {
        this.saved.emit(fresh);
        this.applyValues(fresh);
      }
      this.conflict.set(false);
      this.notify.info('Dados recarregados', 'O formulário mostra a versão mais recente.');
    } catch (error) {
      this.notify.error(
        error instanceof ApiError ? error : 'Não foi possível recarregar a atribuição.',
      );
    } finally {
      this.reloading.set(false);
    }
  }

  protected close(): void {
    this.visible.set(false);
  }

  private async loadLessons(): Promise<void> {
    if (this.isEdit()) {
      return;
    }

    this.lessonsLoading.set(true);
    this.lessonsError.set('');
    try {
      this.lessonOptions.set(await this.lessons.publishedOptions());
    } catch (error) {
      this.lessonsError.set(
        error instanceof ApiError ? error.detail : 'Não foi possível carregar o acervo.',
      );
    } finally {
      this.lessonsLoading.set(false);
    }
  }

  private reset(): void {
    this.conflict.set(false);
    this.summaryErrors.set([]);
    this.dateRangeInvalid.set(false);

    const current = this.assignment();
    if (current) {
      this.applyValues(current);
      return;
    }

    this.form.reset({
      lessonId: null,
      status: 'DRAFT',
      availableFrom: null,
      dueAt: null,
      timeLimitMinutes: DEFAULT_TIME_LIMIT_MINUTES,
      maxAttempts: DEFAULT_MAX_ATTEMPTS,
      questionCount: null,
      shuffleQuestions: true,
      shuffleOptions: true,
    });
    this.noTimeLimit.set(false);
    this.noAttemptLimit.set(false);
    this.allQuestions.set(true);
    this.onDateChange();
  }

  private applyValues(assignment: LessonAssignment): void {
    this.form.reset({
      lessonId: assignment.lesson.id,
      status: assignment.status,
      availableFrom: instantToPickedDate(assignment.availableFrom),
      dueAt: instantToPickedDate(assignment.dueAt),
      timeLimitMinutes: assignment.timeLimitMinutes,
      maxAttempts: assignment.maxAttempts,
      questionCount: assignment.questionCount,
      shuffleQuestions: assignment.shuffleQuestions,
      shuffleOptions: assignment.shuffleOptions,
    });
    this.noTimeLimit.set(assignment.timeLimitMinutes === null);
    this.noAttemptLimit.set(assignment.maxAttempts === null);
    this.allQuestions.set(assignment.questionCount === null);
    this.onDateChange();
  }

  private body() {
    const value = this.form.getRawValue();
    return {
      status: value.status,
      availableFrom: pickedDateToInstant(value.availableFrom),
      dueAt: pickedDateToInstant(value.dueAt),
      // Os três `null` abaixo são o contrato de "sem limite" da §7.3.
      timeLimitMinutes: this.noTimeLimit() ? null : value.timeLimitMinutes,
      maxAttempts: this.noAttemptLimit() ? null : value.maxAttempts,
      questionCount: this.allQuestions() ? null : value.questionCount,
      shuffleQuestions: value.shuffleQuestions,
      shuffleOptions: value.shuffleOptions,
    };
  }

  private createBody(): CreateAssignmentRequest {
    const lessonId = this.form.controls.lessonId.value;
    return {
      lessonId: lessonId ?? '',
      position: this.nextPosition(),
      ...this.body(),
    };
  }

  private patchBody(version: number): PatchAssignmentRequest {
    return { version, ...this.body() };
  }

  private handleError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível salvar a atribuição.');
      return;
    }

    if (error.isVersionConflict) {
      this.conflict.set(true);
      return;
    }

    if (error.isValidation) {
      // Formulário preservado; o `422` de "questões insuficientes" chega junto
      // do campo `questionCount` quando o servidor o identifica.
      const { orphans } = applyFieldErrors(this.form, error);
      const messages = orphans.map((item) => `${item.field}: ${item.message}`);
      this.summaryErrors.set(
        messages.length > 0 || error.fieldErrors.length > 0 ? messages : [error.detail],
      );
      return;
    }

    this.notify.error(error);
  }
}
