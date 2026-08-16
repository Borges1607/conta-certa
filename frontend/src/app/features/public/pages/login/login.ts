import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';

import { ApiError } from '../../../../core/api/problem-details';
import { AuthService } from '../../../../core/auth/auth.service';
import { AuthStore, homePathForRole } from '../../../../core/auth/auth.store';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { applyFieldErrors, autoClearServerErrors } from '../../../../shared/forms/apply-field-errors';
import { AuthCardComponent } from '../../components/auth-card/auth-card';

/** O bloqueio pede uma ação diferente conforme o motivo. */
type Blocker = 'unverified' | 'inactive' | null;

/**
 * Login — Parte 3, §3.
 *
 * Único para os três perfis; o redirecionamento é por `user.role`.
 */
@Component({
  selector: 'cc-login-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    InputText,
    Password,
    Message,
    AuthCardComponent,
    FormFieldComponent,
    SubmitButtonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthStore);
  private readonly authApi = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notify = inject(NotificationService);

  protected readonly guard = createSubmitGuard();
  protected readonly resendGuard = createSubmitGuard();

  protected readonly formError = signal('');
  protected readonly blocker = signal<Blocker>(null);
  protected readonly resendSent = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  constructor() {
    autoClearServerErrors(this.form);
  }

  protected async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    this.formError.set('');
    this.blocker.set(null);
    this.resendSent.set(false);

    await this.guard.run(async () => {
      try {
        const user = await this.auth.login(this.form.getRawValue());
        await this.router.navigateByUrl(this.destinationFor(user.mustChangePassword, user.role));
      } catch (error) {
        this.handleFailure(error);
      }
    });
  }

  /** Reenvia a confirmação sem sair da tela de login. */
  protected async resendVerification(): Promise<void> {
    const email = this.form.controls.email.value;
    if (!email) {
      return;
    }

    await this.resendGuard.run(async () => {
      try {
        await firstValueFrom(this.authApi.resendVerification(email));
      } catch {
        // A resposta é sempre a mesma, exista o e-mail ou não: revelar a
        // diferença entregaria quais e-mails estão cadastrados.
      }
      this.resendSent.set(true);
    });
  }

  private destinationFor(mustChangePassword: boolean, role: 'ADMIN' | 'TEACHER' | 'STUDENT'): string {
    // A troca obrigatória vence inclusive o returnUrl.
    if (mustChangePassword) {
      this.notify.warn('Troque sua senha', 'É necessário definir uma nova senha para continuar.');
      return '/conta/senha';
    }

    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    const home = homePathForRole(role);

    // Só aceitamos um returnUrl interno e compatível com o perfil.
    if (returnUrl?.startsWith('/') && !returnUrl.startsWith('//') && returnUrl.startsWith(areaOf(home))) {
      return returnUrl;
    }
    return home;
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.formError.set('Algo deu errado. Tente novamente.');
      return;
    }

    if (error.fieldErrors.length > 0) {
      applyFieldErrors(this.form, error);
      return;
    }

    switch (error.status) {
      case 401:
        // Mensagem única: dizer qual campo errou revelaria se o e-mail existe.
        this.formError.set('E-mail ou senha inválidos.');
        break;
      case 403:
        this.applyBlocker(error);
        break;
      case 429:
        this.formError.set(error.detail);
        break;
      default:
        this.formError.set(error.detail);
    }
  }

  private applyBlocker(error: ApiError): void {
    const code = error.code.toUpperCase();
    if (code.includes('VERIF') || code.includes('PENDING')) {
      this.blocker.set('unverified');
      return;
    }
    if (code.includes('INACTIVE') || code.includes('DISABLED')) {
      this.blocker.set('inactive');
      return;
    }
    this.formError.set(error.detail);
  }
}

/** `/aluno/salas` → `/aluno`; usado para conferir se o returnUrl é da área certa. */
function areaOf(homePath: string): string {
  const [, area] = homePath.split('/');
  return `/${area}`;
}
