import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Tag } from 'primeng/tag';

import { ApiError } from '../../../../core/api/problem-details';
import { AuthStore } from '../../../../core/auth/auth.store';
import { ROLE_LABELS } from '../../../../core/models/labels';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { StatusTagComponent } from '../../../../shared/components/status-tag/status-tag';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import {
  applyFieldErrors,
  autoClearServerErrors,
} from '../../../../shared/forms/apply-field-errors';
import { formatCnpj } from '../../../../shared/forms/validators';

/**
 * Perfil — Parte 3, §8.
 *
 * **Somente o nome é editável.** E-mail, matrícula e instituição exigem
 * suporte administrativo e estão fora do escopo desta versão (§4.1 da spec).
 */
@Component({
  selector: 'cc-profile-page',
  imports: [
    ReactiveFormsModule,
    InputText,
    Message,
    Tag,
    PageHeaderComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    StatusTagComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class ProfilePage {
  private readonly fb = inject(FormBuilder);
  private readonly notify = inject(NotificationService);

  protected readonly auth = inject(AuthStore);
  protected readonly guard = createSubmitGuard();
  protected readonly formError = signal('');

  protected readonly form = this.fb.nonNullable.group({
    fullName: [this.auth.user()?.fullName ?? '', [Validators.required, Validators.minLength(3)]],
  });

  constructor() {
    autoClearServerErrors(this.form);
  }

  protected roleLabel(): string {
    const role = this.auth.role();
    return role ? ROLE_LABELS[role] : '—';
  }

  protected cnpj(value: string): string {
    return formatCnpj(value);
  }

  protected async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    this.formError.set('');

    await this.guard.run(async () => {
      try {
        await this.auth.updateProfile({ fullName: this.form.controls.fullName.value });
        this.notify.success('Perfil atualizado');
      } catch (error) {
        if (error instanceof ApiError) {
          applyFieldErrors(this.form, error);
          if (error.fieldErrors.length === 0) {
            this.formError.set(error.detail);
          }
        } else {
          this.formError.set('Algo deu errado. Tente novamente.');
        }
      }
    });
  }
}
