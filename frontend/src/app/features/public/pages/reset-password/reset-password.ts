import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Button } from 'primeng/button';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';

import { ApiError } from '../../../../core/api/problem-details';
import { AuthService } from '../../../../core/auth/auth.service';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import {
  applyFieldErrors,
  autoClearServerErrors,
} from '../../../../shared/forms/apply-field-errors';
import { passwordMatchValidator, passwordRuleValidator } from '../../../../shared/forms/validators';
import { AuthCardComponent } from '../../components/auth-card/auth-card';
import { PasswordRequirementsComponent } from '../../../../shared/components/password-requirements/password-requirements';

/**
 * Redefinição de senha — Parte 3, §6.
 *
 * Sucesso leva ao login **sem autenticar automaticamente**: quem redefine a
 * senha precisa provar que sabe a nova.
 */
@Component({
  selector: 'cc-reset-password-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    Password,
    Message,
    AuthCardComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    PasswordRequirementsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.scss',
})
export class ResetPasswordPage {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  protected readonly guard = createSubmitGuard();
  protected readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  protected readonly tokenGone = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.fb.nonNullable.group(
    {
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

    await this.guard.run(async () => {
      try {
        await firstValueFrom(
          this.authApi.resetPassword({
            token: this.token,
            newPassword: this.form.controls.newPassword.value,
          }),
        );
        this.notify.success('Senha redefinida', 'Entre com a nova senha.');
        await this.router.navigate(['/login']);
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

    if (error.isGone || error.isNotFound) {
      this.tokenGone.set(true);
      return;
    }

    applyFieldErrors(this.form, error);
    if (error.fieldErrors.length === 0) {
      this.formError.set(error.detail);
    }
  }
}
