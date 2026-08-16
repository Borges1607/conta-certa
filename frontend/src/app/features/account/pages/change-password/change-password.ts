import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';

import { ApiError } from '../../../../core/api/problem-details';
import { AuthStore } from '../../../../core/auth/auth.store';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import {
  applyFieldErrors,
  autoClearServerErrors,
} from '../../../../shared/forms/apply-field-errors';
import { passwordMatchValidator, passwordRuleValidator } from '../../../../shared/forms/validators';
import { PasswordRequirementsComponent } from '../../../../shared/components/password-requirements/password-requirements';

/**
 * Troca de senha — Parte 3, §8.
 *
 * Quando o usuário chegou por `mustChangePassword`, a tela explica a
 * obrigatoriedade e é o único destino autenticado disponível até a conclusão
 * (visão geral, §5).
 */
@Component({
  selector: 'cc-change-password-page',
  imports: [
    ReactiveFormsModule,
    Password,
    Message,
    PageHeaderComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    PasswordRequirementsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-password.html',
  styleUrl: './change-password.scss',
})
export class ChangePasswordPage {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  protected readonly auth = inject(AuthStore);
  protected readonly guard = createSubmitGuard();
  protected readonly formError = signal('');

  protected readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, passwordRuleValidator]],
      passwordConfirm: ['', [Validators.required]],
    },
    { validators: passwordMatchValidator('newPassword', 'passwordConfirm') },
  );

  constructor() {
    autoClearServerErrors(this.form);
  }

  protected async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    this.formError.set('');
    const wasMandatory = this.auth.mustChangePassword();

    await this.guard.run(async () => {
      try {
        await this.auth.changePassword({
          currentPassword: this.form.controls.currentPassword.value,
          newPassword: this.form.controls.newPassword.value,
        });

        this.notify.success('Senha alterada');
        this.form.reset();

        if (wasMandatory) {
          // A obrigatoriedade acabou: o usuário segue para a área dele.
          await this.router.navigateByUrl(this.auth.homePath());
        }
      } catch (error) {
        this.handleFailure(error);
      }
    });
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.formError.set('Algo deu errado. Tente novamente.');
      return;
    }

    if (error.status === 401 || error.status === 422) {
      applyFieldErrors(this.form, error);
      if (error.fieldErrors.length === 0) {
        this.form.controls.currentPassword.setErrors({ server: 'Senha atual incorreta.' });
      }
      return;
    }

    this.formError.set(error.detail);
  }
}
