import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { ToggleSwitch } from 'primeng/toggleswitch';

import { NotificationService } from '../../../../core/notifications/notification.service';
import { toLocalDateString } from '../../../../core/util/format';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { autoClearServerErrors, markAllTouched } from '../../../../shared/forms/apply-field-errors';
import { FinancialTipService } from '../../data/financial-tip.service';
import type {
  CreateFinancialTipRequest,
  FinancialTip,
  PatchFinancialTipRequest,
} from '../../models/financial-tip-dto';
import { handleFormError, notifyError } from '../../util/form-errors';
import { parseLocalDate } from '../../util/local-date';
import { MarkdownEditorComponent } from '../markdown-editor/markdown-editor';
import { VersionConflictNoticeComponent } from '../version-conflict-notice/version-conflict-notice';

/**
 * Criação e edição de dica financeira — Parte 6, §6.
 *
 * O cuidado que justifica este componente existir separado é a data.
 * `publicationDate` é um `LocalDate`: o seletor trabalha com `Date`, mas a
 * conversão nos dois sentidos passa por `parseLocalDate` e `toLocalDateString`,
 * que usam apenas componentes de calendário locais. Nenhum `toISOString()`,
 * nenhum `new Date('2026-08-15')` — os dois deslocariam o dia conforme o fuso
 * do navegador, que é exatamente o erro que a spec manda evitar.
 *
 * `active` só aparece na criação. Em uma dica existente, ativar e desativar são
 * ações explícitas da lista, com o endpoint próprio — não um interruptor que
 * muda o estado de publicação de raspão enquanto se corrige um texto.
 */
@Component({
  selector: 'cc-tip-form-dialog',
  imports: [
    ReactiveFormsModule,
    Dialog,
    Button,
    InputText,
    DatePicker,
    ToggleSwitch,
    Message,
    FormFieldComponent,
    SubmitButtonComponent,
    MarkdownEditorComponent,
    VersionConflictNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tip-form-dialog.html',
  styleUrl: './tip-form-dialog.scss',
})
export class TipFormDialogComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly service = inject(FinancialTipService);
  private readonly notification = inject(NotificationService);

  readonly visible = input.required<boolean>();
  readonly tip = input<FinancialTip | null>(null);

  readonly closed = output<void>();
  readonly saved = output<FinancialTip>();

  protected readonly guard = createSubmitGuard();
  protected readonly reloading = signal(false);
  protected readonly conflict = signal(false);

  private readonly current = signal<FinancialTip | null>(null);

  protected readonly isEdit = computed(() => this.current() !== null);
  protected readonly header = computed(() => (this.isEdit() ? 'Editar dica' : 'Nova dica'));

  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(160)]],
    content: ['', [Validators.required]],
    sourceUrl: [''],
    publicationDate: this.fb.control<Date | null>(null, Validators.required),
    active: [true],
  });

  constructor() {
    autoClearServerErrors(this.form);

    effect(() => {
      if (this.visible()) {
        this.reset(this.tip());
      }
    });
  }

  protected close(): void {
    this.closed.emit();
  }

  protected onVisibleChange(visible: boolean): void {
    if (!visible) {
      this.close();
    }
  }

  protected submit(): void {
    markAllTouched(this.form);
    if (this.form.invalid || this.conflict()) {
      return;
    }

    void this.guard.run(async () => {
      try {
        const existing = this.current();
        const saved = existing
          ? await this.service.update(existing.id, this.patchBody(existing.version))
          : await this.service.create(this.createBody());

        this.notification.success(existing ? 'Dica atualizada' : 'Dica criada', saved.title);
        this.saved.emit(saved);
      } catch (error) {
        handleFormError(error, this.form, this.notification, {
          onVersionConflict: () => this.conflict.set(true),
          duplicateField: {
            name: 'publicationDate',
            message: 'Já existe uma dica agendada para esta data.',
          },
        });
      }
    });
  }

  protected reload(): void {
    const existing = this.current();
    if (!existing) {
      return;
    }

    this.reloading.set(true);
    void this.service
      .get(existing.id)
      .then((fresh) => {
        this.reset(fresh);
        this.notification.info('Dados recarregados', 'Confira os valores antes de salvar.');
      })
      .catch((error: unknown) => notifyError(error, this.notification))
      .finally(() => this.reloading.set(false));
  }

  private reset(tip: FinancialTip | null): void {
    this.current.set(tip);
    this.conflict.set(false);
    this.form.reset({
      title: tip?.title ?? '',
      content: tip?.content ?? '',
      sourceUrl: tip?.sourceUrl ?? '',
      // Texto `YYYY-MM-DD` → `Date` local. Nunca `new Date(texto)`.
      publicationDate: parseLocalDate(tip?.publicationDate),
      active: tip?.active ?? true,
    });
  }

  /** `Date` do seletor → `YYYY-MM-DD`, sem hora e sem conversão de fuso. */
  private publicationDate(): string {
    const value = this.form.controls.publicationDate.value;
    return value ? toLocalDateString(value) : '';
  }

  private createBody(): CreateFinancialTipRequest {
    const value = this.form.getRawValue();
    return {
      title: value.title.trim(),
      content: value.content,
      sourceUrl: value.sourceUrl.trim() || null,
      publicationDate: this.publicationDate(),
      active: value.active,
    };
  }

  private patchBody(version: number): PatchFinancialTipRequest {
    const value = this.form.getRawValue();
    return {
      title: value.title.trim(),
      content: value.content,
      sourceUrl: value.sourceUrl.trim() || null,
      publicationDate: this.publicationDate(),
      version,
    };
  }
}
