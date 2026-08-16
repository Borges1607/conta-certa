import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Button } from 'primeng/button';

import { ApiError } from '../../../../core/api/problem-details';
import type { Grade } from '../../../../core/models/enums';
import { GRADE_LABELS } from '../../../../core/models/labels';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { applyFieldErrors, markAllTouched } from '../../../../shared/forms/apply-field-errors';
import { TeacherRoomService } from '../../data/teacher-room.service';
import {
  DEFAULT_PASSING_SCORE_PERCENT,
  type CreateRoomRequest,
  type PatchRoomRequest,
  type TeacherRoomDetail,
  type TeacherRoomSummary,
} from '../../models/room';
import { TopicsInputComponent } from '../topics-input/topics-input';
import { VersionConflictNoticeComponent } from '../version-conflict-notice/version-conflict-notice';

interface GradeOption {
  value: Grade;
  label: string;
}

/**
 * Criação e edição de sala — Parte 5, §4.
 *
 * A instituição **não** é campo: a API a deriva do professor autenticado
 * (critério de aceite da Parte 5, §12).
 *
 * Na edição, `version` acompanha o `PATCH`. Um `409 VERSION_CONFLICT` troca o
 * rodapé por um aviso com **Recarregar** — nunca por um "salvar mesmo assim".
 */
@Component({
  selector: 'cc-room-form-dialog',
  imports: [
    ReactiveFormsModule,
    Dialog,
    Button,
    InputText,
    InputNumber,
    Select,
    Textarea,
    FormFieldComponent,
    SubmitButtonComponent,
    TopicsInputComponent,
    VersionConflictNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-form-dialog.html',
  styleUrl: './room-form-dialog.scss',
})
export class RoomFormDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly rooms = inject(TeacherRoomService);
  private readonly notify = inject(NotificationService);

  readonly visible = model(false);
  /** `null` cria; preenchido edita. */
  readonly room = input<TeacherRoomSummary | TeacherRoomDetail | null>(null);

  readonly saved = output<TeacherRoomDetail>();

  protected readonly guard = createSubmitGuard();
  protected readonly conflict = signal(false);
  protected readonly reloading = signal(false);
  protected readonly topics = signal<string[]>([]);
  protected readonly summaryErrors = signal<string[]>([]);

  protected readonly gradeOptions: GradeOption[] = (
    Object.keys(GRADE_LABELS) as Grade[]
  ).map((value) => ({ value, label: GRADE_LABELS[value] }));

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: [''],
    grade: ['HIGH_SCHOOL_1' as Grade, [Validators.required]],
    passingScorePercent: [
      DEFAULT_PASSING_SCORE_PERCENT,
      [Validators.required, Validators.min(0), Validators.max(100)],
    ],
  });

  protected readonly isEdit = computed(() => this.room() !== null);
  protected readonly title = computed(() => (this.isEdit() ? 'Editar sala' : 'Nova sala'));

  constructor() {
    // Reabrir o diálogo sempre recomeça do estado do recurso, nunca do que
    // sobrou da edição anterior.
    effect(() => {
      if (!this.visible()) {
        return;
      }
      this.reset();
    });
  }

  protected onTopicsChange(topics: string[]): void {
    this.topics.set(topics);
  }

  protected async submit(): Promise<void> {
    markAllTouched(this.form);
    if (this.form.invalid || this.conflict()) {
      return;
    }

    this.summaryErrors.set([]);
    const value = this.form.getRawValue();

    await this.guard.run(async () => {
      try {
        const current = this.room();
        const result = current
          ? await this.rooms.update(current.id, this.patchBody(current.version, value))
          : await this.rooms.create(this.createBody(value));

        this.notify.success(
          current ? 'Sala atualizada' : 'Sala criada',
          current ? undefined : `Código de ingresso: ${result.joinCode}`,
        );
        this.saved.emit(result);
        this.visible.set(false);
      } catch (error) {
        this.handleError(error);
      }
    });
  }

  /** Recarrega o recurso do servidor e volta o formulário ao estado atual. */
  protected async reload(): Promise<void> {
    const current = this.room();
    if (!current || this.reloading()) {
      return;
    }

    this.reloading.set(true);
    try {
      const fresh = await this.rooms.get(current.id);
      this.saved.emit(fresh);
      this.applyValues(fresh);
      this.conflict.set(false);
      this.notify.info('Dados recarregados', 'O formulário agora mostra a versão mais recente.');
    } catch (error) {
      this.notify.error(
        error instanceof ApiError ? error : 'Não foi possível recarregar a sala.',
      );
    } finally {
      this.reloading.set(false);
    }
  }

  protected close(): void {
    this.visible.set(false);
  }

  private reset(): void {
    this.conflict.set(false);
    this.summaryErrors.set([]);
    const current = this.room();
    if (current) {
      this.applyValues(current);
    } else {
      this.form.reset({
        name: '',
        description: '',
        grade: 'HIGH_SCHOOL_1',
        passingScorePercent: DEFAULT_PASSING_SCORE_PERCENT,
      });
      this.topics.set([]);
    }
  }

  private applyValues(room: TeacherRoomSummary | TeacherRoomDetail): void {
    this.form.reset({
      name: room.name,
      description: room.description ?? '',
      grade: room.grade,
      passingScorePercent: room.passingScorePercent,
    });
    this.topics.set([...room.contentTopics]);
  }

  private createBody(value: {
    name: string;
    description: string;
    grade: Grade;
    passingScorePercent: number;
  }): CreateRoomRequest {
    return {
      name: value.name.trim(),
      description: value.description.trim() || null,
      grade: value.grade,
      contentTopics: this.topics(),
      passingScorePercent: value.passingScorePercent,
    };
  }

  /** `version` entra sempre — é o que permite detectar o conflito. */
  private patchBody(
    version: number,
    value: { name: string; description: string; grade: Grade; passingScorePercent: number },
  ): PatchRoomRequest {
    return { version, ...this.createBody(value) };
  }

  private handleError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível salvar a sala.');
      return;
    }

    if (error.isVersionConflict) {
      // Formulário preservado; ação única: recarregar.
      this.conflict.set(true);
      return;
    }

    if (error.isValidation) {
      const { orphans } = applyFieldErrors(this.form, error);
      this.summaryErrors.set(orphans.map((item) => `${item.field}: ${item.message}`));
      if (orphans.length === 0 && error.fieldErrors.length === 0) {
        this.summaryErrors.set([error.detail]);
      }
      return;
    }

    this.notify.error(error);
  }
}
