import { Routes } from '@angular/router';

import { guestGuard } from '../../core/auth/guards';

/**
 * Rotas públicas — Parte 3, §2.
 *
 * `guestGuard` só protege as portas de entrada (login e cadastro). As telas de
 * token — confirmação, redefinição, convite — ficam acessíveis mesmo com
 * sessão: um professor logado pode receber um link de convite de outra conta.
 */
export const publicRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./layout/public-shell/public-shell').then((m) => m.PublicShellComponent),
    children: [
      {
        path: 'login',
        canActivate: [guestGuard],
        title: 'Entrar · Conta Certa',
        loadComponent: () => import('./pages/login/login').then((m) => m.LoginPage),
      },
      {
        path: 'cadastro',
        canActivate: [guestGuard],
        title: 'Criar conta · Conta Certa',
        loadComponent: () =>
          import('./pages/student-registration/student-registration').then(
            (m) => m.StudentRegistrationPage,
          ),
      },
      {
        path: 'verificar-email',
        title: 'Confirmar e-mail · Conta Certa',
        loadComponent: () =>
          import('./pages/verify-email/verify-email').then((m) => m.VerifyEmailPage),
      },
      {
        path: 'esqueci-senha',
        title: 'Recuperar senha · Conta Certa',
        loadComponent: () =>
          import('./pages/forgot-password/forgot-password').then((m) => m.ForgotPasswordPage),
      },
      {
        path: 'redefinir-senha',
        title: 'Nova senha · Conta Certa',
        loadComponent: () =>
          import('./pages/reset-password/reset-password').then((m) => m.ResetPasswordPage),
      },
      {
        path: 'convite-professor',
        title: 'Aceitar convite · Conta Certa',
        loadComponent: () =>
          import('./pages/accept-teacher-invite/accept-teacher-invite').then(
            (m) => m.AcceptTeacherInvitePage,
          ),
      },
    ],
  },
];
