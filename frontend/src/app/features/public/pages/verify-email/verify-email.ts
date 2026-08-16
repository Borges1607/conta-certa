import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';

import { ApiError } from '../../../../core/api/problem-details';
import { AuthService } from '../../../../core/auth/auth.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { AuthCardComponent } from '../../components/auth-card/auth-card';

/** Os seis estados exigidos pela §5.1 da spec de integração. */
type VerifyState = 'checking' | 'confirmed' | 'expired' | 'used' | 'invalid' | 'no-token';

@Component({
  selector: 'cc-verify-email-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    InputText,
    Message,
    AuthCardComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    LoadingSkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './verify-email.html',
  styleUrl: './verify-email.scss',
})
export class VerifyEmailPage {
  private readonly fb = inject(FormBuilder);
  private readonly authApi = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly state = signal<VerifyState>('checking');
  protected readonly resendGuard = createSubmitGuard();
  protected readonly resendSent = signal(false);

  protected readonly resendForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  /** Expirado, inválido e sem token compartilham o formulário de reenvio, mas
   *  não a explicação: o usuário precisa saber o que aconteceu. */
  protected readonly heading = computed(() => {
    switch (this.state()) {
      case 'expired':
        return 'Link expirado';
      case 'invalid':
        return 'Link inválido';
      default:
        return 'Reenviar confirmação';
    }
  });

  protected readonly description = computed(() => {
    switch (this.state()) {
      case 'expired':
        return 'Podemos enviar um novo agora.';
      case 'invalid':
        return 'Confira se o endereço foi copiado por inteiro.';
      default:
        return 'Informe o e-mail do seu cadastro.';
    }
  });

  constructor() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('no-token');
      return;
    }
    void this.verify(token);
  }

  protected async resend(): Promise<void> {
    this.resendForm.markAllAsTouched();
    if (this.resendForm.invalid) {
      return;
    }

    await this.resendGuard.run(async () => {
      try {
        await firstValueFrom(this.authApi.resendVerification(this.resendForm.controls.email.value));
      } catch {
        // A resposta é sempre igual, exista o e-mail ou não: revelar a
        // diferença entregaria quais e-mails estão cadastrados.
      }
      this.resendSent.set(true);
    });
  }

  private async verify(token: string): Promise<void> {
    try {
      await firstValueFrom(this.authApi.verifyEmail(token));
      this.state.set('confirmed');
    } catch (error) {
      this.state.set(stateForError(error));
    }
  }
}

function stateForError(error: unknown): VerifyState {
  if (!(error instanceof ApiError)) {
    return 'invalid';
  }
  switch (error.status) {
    case 410:
      return 'expired';
    case 409:
      // Já confirmado: não é falha do usuário, o tom precisa ser neutro.
      return 'used';
    default:
      return 'invalid';
  }
}
