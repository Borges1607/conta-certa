import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { Select } from 'primeng/select';

import { ApiError } from '../../../../core/api/problem-details';
import { AuthService } from '../../../../core/auth/auth.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import {
  applyFieldErrors,
  autoClearServerErrors,
} from '../../../../shared/forms/apply-field-errors';
import { createPageState } from '../../../../shared/forms/page-state';
import { passwordMatchValidator, passwordRuleValidator } from '../../../../shared/forms/validators';
import { AuthCardComponent } from '../../components/auth-card/auth-card';
import { PasswordRequirementsComponent } from '../../../../shared/components/password-requirements/password-requirements';
import { InstitutionOptionsService } from '../../data/institution-options.service';

/**
 * Cadastro de aluno — Parte 3, §4.
 *
 * Exclusivo para aluno: a spec não prevê autocadastro de professor ou admin.
 * O sucesso é `202 Accepted` e **não** autentica — o aluno só entra depois de
 * confirmar o e-mail (§4.1 da spec de integração).
 */
@Component({
  selector: 'cc-student-registration-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    InputText,
    Password,
    Select,
    Message,
    AuthCardComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    LoadingSkeletonComponent,
    PasswordRequirementsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-registration.html',
  styleUrl: './student-registration.scss',
})
export class StudentRegistrationPage {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthService);
  private readonly institutions = inject(InstitutionOptionsService);

  protected readonly guard = createSubmitGuard();
  protected readonly resendGuard = createSubmitGuard();

  protected readonly formError = signal('');
  protected readonly orphanErrors = signal<string[]>([]);
  protected readonly registeredEmail = signal<string | null>(null);
  protected readonly resendSent = signal(false);

  protected readonly options = createPageState(() => this.institutions.listActive());

  protected readonly form = this.fb.nonNullable.group(
    {
      fullName: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      registrationNumber: ['', [Validators.required]],
      institutionId: ['', [Validators.required]],
      password: ['', [Validators.required, passwordRuleValidator]],
      passwordConfirm: ['', [Validators.required]],
    },
    { validators: passwordMatchValidator('password', 'passwordConfirm') },
  );

  constructor() {
    autoClearServerErrors(this.form);
    void this.options.load();
  }

  protected async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    this.formError.set('');
    this.orphanErrors.set([]);

    await this.guard.run(async () => {
      const { passwordConfirm: _ignored, ...payload } = this.form.getRawValue();

      try {
        await firstValueFrom(this.authApi.registerStudent(payload));
        // Troca o formulário pela confirmação. Nenhuma sessão é criada.
        this.registeredEmail.set(payload.email);
      } catch (error) {
        this.handleFailure(error);
      }
    });
  }

  protected async resend(): Promise<void> {
    const email = this.registeredEmail();
    if (!email) {
      return;
    }

    await this.resendGuard.run(async () => {
      try {
        await firstValueFrom(this.authApi.resendVerification(email));
      } catch {
        // Resposta uniforme, para não revelar cadastros.
      }
      this.resendSent.set(true);
    });
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.formError.set('Algo deu errado. Tente novamente.');
      return;
    }

    // O formulário nunca é limpo: os valores digitados permanecem.
    const { orphans } = applyFieldErrors(this.form, error);
    this.orphanErrors.set(orphans.map((o) => o.message));

    if (error.fieldErrors.length === 0) {
      this.formError.set(error.detail);
    }
  }
}
