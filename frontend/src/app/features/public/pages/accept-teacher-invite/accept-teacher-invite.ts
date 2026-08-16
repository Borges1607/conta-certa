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
 * Aceite de convite do professor — Parte 3, §7.
 *
 * O professor **não se convida**: o convite vem da administração. Se o token
 * expirou, o caminho é pedir um novo à coordenação, não recomeçar aqui.
 */
@Component({
  selector: 'cc-accept-teacher-invite-page',
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
  templateUrl: './accept-teacher-invite.html',
  styleUrl: './accept-teacher-invite.scss',
})
export class AcceptTeacherInvitePage {
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
      password: ['', [Validators.required, passwordRuleValidator]],
      passwordConfirm: ['', [Validators.required]],
    },
    { validators: passwordMatchValidator('password', 'passwordConfirm') },
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
          this.authApi.acceptTeacherInvite({
            token: this.token,
            password: this.form.controls.password.value,
          }),
        );
        this.notify.success('Conta ativada', 'Entre com seu e-mail e a senha que você definiu.');
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
