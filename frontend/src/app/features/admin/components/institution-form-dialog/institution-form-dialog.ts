import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';

import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import {
  autoClearServerErrors,
  markAllTouched,
} from '../../../../shared/forms/apply-field-errors';
import { InstitutionService } from '../../data/institution.service';
import type {
  AdminInstitution,
  CreateInstitutionRequest,
  PatchInstitutionRequest,
} from '../../models/institution-dto';
import { cnpjValidator } from '../../util/cnpj';
import { handleFormError, notifyError } from '../../util/form-errors';
import { phoneValidator } from '../../util/phone';
import { CnpjInputComponent } from '../cnpj-input/cnpj-input';
import { PhoneInputComponent } from '../phone-input/phone-input';
import { VersionConflictNoticeComponent } from '../version-conflict-notice/version-conflict-notice';

/**
 * Criação e edição de instituição — Parte 6, §4.
 *
 * Dois detalhes de contrato ficam garantidos aqui:
 *
 * 1. o CNPJ sai com 14 dígitos porque o controle guarda o valor normalizado
 *    (ver `cc-cnpj-input`);
 * 2. a edição sempre envia `version`, porque `PatchInstitutionRequest` a exige
 *    e o valor vem do recurso carregado, não de um campo do formulário.
 */
@Component({
  selector: 'cc-institution-form-dialog',
  imports: [
    ReactiveFormsModule,
    Dialog,
    Button,
    InputText,
    FormFieldComponent,
    SubmitButtonComponent,
    CnpjInputComponent,
    PhoneInputComponent,
    VersionConflictNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './institution-form-dialog.html',
  styleUrl: './institution-form-dialog.scss',
})
export class InstitutionFormDialogComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly service = inject(InstitutionService);
  private readonly notification = inject(NotificationService);

  readonly visible = input.required<boolean>();
  /** `null` cria; preenchido edita. */
  readonly institution = input<AdminInstitution | null>(null);

  readonly closed = output<void>();
  readonly saved = output<AdminInstitution>();

  protected readonly guard = createSubmitGuard();
  protected readonly reloading = signal(false);
  protected readonly conflict = signal(false);

  /** Cópia carregada do recurso: dela sai o `version` enviado no `PATCH`. */
  private readonly current = signal<AdminInstitution | null>(null);

  protected readonly isEdit = computed(() => this.current() !== null);
  protected readonly header = computed(() =>
    this.isEdit() ? 'Editar instituição' : 'Nova instituição',
  );

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    cnpj: ['', [Validators.required, cnpjValidator]],
    contactEmail: ['', [Validators.required, Validators.email]],
    contactPhone: ['', [Validators.required, phoneValidator]],
  });

  constructor() {
    // Erro de servidor se refere ao valor antigo: some assim que o usuário digita.
    autoClearServerErrors(this.form);

    effect(() => {
      if (this.visible()) {
        this.reset(this.institution());
      }
    });
  }

  protected close(): void {
    this.closed.emit();
  }

  /** O `p-dialog` avisa quando se fecha sozinho (ESC, máscara, botão X). */
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

        this.notification.success(
          existing ? 'Instituição atualizada' : 'Instituição criada',
          saved.name,
        );
        this.saved.emit(saved);
      } catch (error) {
        handleFormError(error, this.form, this.notification, {
          onVersionConflict: () => this.conflict.set(true),
          duplicateField: {
            name: 'cnpj',
            message: 'Já existe uma instituição cadastrada com este CNPJ.',
          },
        });
      }
    });
  }

  /** Única saída de um conflito: buscar o estado atual e recomeçar dele. */
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

  private reset(institution: AdminInstitution | null): void {
    this.current.set(institution);
    this.conflict.set(false);
    this.form.reset({
      name: institution?.name ?? '',
      cnpj: institution?.cnpj ?? '',
      contactEmail: institution?.contactEmail ?? '',
      contactPhone: institution?.contactPhone ?? '',
    });
  }

  private createBody(): CreateInstitutionRequest {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      cnpj: value.cnpj,
      contactEmail: value.contactEmail.trim(),
      contactPhone: value.contactPhone,
    };
  }

  private patchBody(version: number): PatchInstitutionRequest {
    return { ...this.createBody(), version };
  }
}
