import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';

import { ApiError } from '../../../../core/api/problem-details';
import { AuthService } from '../../../../core/auth/auth.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { AuthCardComponent } from '../../components/auth-card/auth-card';

/**
 * Solicitação de recuperação — Parte 3, §6.
 *
 * A resposta é **sempre a mesma**, exista o e-mail ou não. Diferenciar
 * revelaria quais e-mails estão cadastrados. A única exceção é `429`, que é
 * sobre o volume de tentativas, não sobre o e-mail.
 */
@Component({
  selector: 'cc-forgot-password-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    InputText,
    Message,
    AuthCardComponent,
    FormFieldComponent,
    SubmitButtonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cc-auth-card
      heading="Recuperar senha"
      description="Informe seu e-mail e enviaremos as instruções."
    >
      @if (sent()) {
        <p-message severity="success" styleClass="forgot__alert">
          Se este e-mail estiver cadastrado, enviaremos as instruções em instantes.
        </p-message>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (rateLimited()) {
            <p-message severity="warn" styleClass="forgot__alert">{{ rateLimited() }}</p-message>
          }

          <cc-form-field label="E-mail" inputId="email" [control]="form.controls.email" [required]="true">
            <input pInputText id="email" type="email" formControlName="email" autocomplete="email" />
          </cc-form-field>

          <cc-submit-button
            label="Enviar instruções"
            icon="pi pi-envelope"
            [submitting]="guard.submitting()"
            [fluid]="true"
          />
        </form>
      }

      <p class="forgot__links"><a routerLink="/login">Voltar ao login</a></p>
    </cc-auth-card>
  `,
  styleUrl: './forgot-password.scss',
})
export class ForgotPasswordPage {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthService);

  protected readonly guard = createSubmitGuard();
  protected readonly sent = signal(false);
  protected readonly rateLimited = signal('');

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    this.rateLimited.set('');

    await this.guard.run(async () => {
      try {
        await firstValueFrom(this.authApi.forgotPassword(this.form.controls.email.value));
      } catch (error) {
        if (error instanceof ApiError && error.isTooManyRequests) {
          this.rateLimited.set(error.detail);
          return;
        }
        // Qualquer outra falha cai na mesma mensagem neutra do sucesso.
      }
      this.sent.set(true);
    });
  }
}
