import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/guards';

/**
 * Telas de conta — Parte 3, §8.
 *
 * Comuns aos três perfis. `/conta/senha` deliberadamente **não** usa
 * `passwordChangeGuard`: ela é justamente o destino de quem está preso pela
 * troca obrigatória.
 */
export const accountRoutes: Routes = [
  {
    path: 'conta',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'perfil' },
      {
        path: 'perfil',
        title: 'Minha conta · Conta Certa',
        loadComponent: () => import('./pages/profile/profile').then((m) => m.ProfilePage),
      },
      {
        path: 'senha',
        title: 'Trocar senha · Conta Certa',
        loadComponent: () =>
          import('./pages/change-password/change-password').then((m) => m.ChangePasswordPage),
      },
    ],
  },
];
