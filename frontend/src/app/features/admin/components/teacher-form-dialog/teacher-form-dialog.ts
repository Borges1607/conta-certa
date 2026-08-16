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
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';

import type { InstitutionOption } from '../../../../core/models/institution';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { autoClearServerErrors, markAllTouched } from '../../../../shared/forms/apply-field-errors';
import { AdminTeacherService } from '../../data/admin-teacher.service';
import { InstitutionService } from '../../data/institution.service';
import type {
  AdminTeacher,
  CreateTeacherRequest,
  PatchTeacherRequest,
} from '../../models/teacher-dto';
import { handleFormError, notifyError } from '../../util/form-errors';
import { VersionConflictNoticeComponent } from '../version-conflict-notice/version-conflict-notice';

/**
 * Criação e edição de professor — Parte 6, §5.
 *
 * **Não existe campo de senha, nem na criação nem na edição.** O admin cria um
 * professor `PENDING` e a API dispara o convite; quem define a senha é o
 * próprio professor. O aviso no topo do formulário diz isso ao usuário, porque
 * é a pergunta que ele faria ao não encontrar o campo.
 *
 * O e-mail só é editável na criação: depois ele identifica a conta e a troca
 * teria de passar por nova verificação.
 */
@Component({
  selector: 'cc-teacher-form-dialog',
  imports: [
    ReactiveFormsModule,
    Dialog,
    Button,
    InputText,
    Select,
    Message,
    FormFieldComponent,
    SubmitButtonComponent,
    VersionConflictNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './teacher-form-dialog.html',
  styleUrl: './teacher-form-dialog.scss',
})
export class TeacherFormDialogComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly service = inject(AdminTeacherService);
  private readonly institutions = inject(InstitutionService);
  private readonly notification = inject(NotificationService);

  readonly visible = input.required<boolean>();
  readonly teacher = input<AdminTeacher | null>(null);

  readonly closed = output<void>();
  readonly saved = output<AdminTeacher>();

  protected readonly guard = createSubmitGuard();
  protected readonly reloading = signal(false);
  protected readonly conflict = signal(false);
  protected readonly options = signal<InstitutionOption[]>([]);
  protected readonly optionsFailed = signal(false);

  private readonly current = signal<AdminTeacher | null>(null);

  protected readonly isEdit = computed(() => this.current() !== null);
  protected readonly header = computed(() =>
    this.isEdit() ? 'Editar professor' : 'Novo professor',
  );
  protected readonly email = computed(() => this.current()?.email ?? '');

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    registrationNumber: ['', [Validators.required, Validators.maxLength(30)]],
    institutionId: ['', [Validators.required]],
  });

  constructor() {
    autoClearServerErrors(this.form);

    effect(() => {
      if (this.visible()) {
        this.reset(this.teacher());
        void this.loadOptions();
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

        if (existing) {
          this.notification.success('Professor atualizado', saved.fullName);
        } else {
          this.notification.success(
            'Convite enviado',
            `${saved.fullName} vai receber um e-mail para definir a própria senha.`,
          );
        }
        this.saved.emit(saved);
      } catch (error) {
        handleFormError(error, this.form, this.notification, {
          onVersionConflict: () => this.conflict.set(true),
          duplicateField: {
            name: 'email',
            message: 'Já existe uma conta com este e-mail.',
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

  /** Só instituições ativas podem receber um professor novo — Parte 6, §5. */
  protected async loadOptions(): Promise<void> {
    try {
      this.options.set(await this.institutions.options());
      this.optionsFailed.set(false);
    } catch {
      this.options.set([]);
      this.optionsFailed.set(true);
    }
  }

  private reset(teacher: AdminTeacher | null): void {
    this.current.set(teacher);
    this.conflict.set(false);
    this.form.reset({
      fullName: teacher?.fullName ?? '',
      email: teacher?.email ?? '',
      registrationNumber: teacher?.registrationNumber ?? '',
      institutionId: teacher?.institution?.id ?? '',
    });

    // E-mail não é editável depois de criado: desabilitar o controle é o que
    // impede o valor de entrar no corpo do `PATCH`.
    if (teacher) {
      this.form.controls.email.disable();
    } else {
      this.form.controls.email.enable();
    }
  }

  private createBody(): CreateTeacherRequest {
    const value = this.form.getRawValue();
    return {
      fullName: value.fullName.trim(),
      email: value.email.trim().toLowerCase(),
      registrationNumber: value.registrationNumber.trim(),
      institutionId: value.institutionId,
    };
  }

  private patchBody(version: number): PatchTeacherRequest {
    const value = this.form.getRawValue();
    return {
      fullName: value.fullName.trim(),
      registrationNumber: value.registrationNumber.trim(),
      institutionId: value.institutionId,
      version,
    };
  }
}
