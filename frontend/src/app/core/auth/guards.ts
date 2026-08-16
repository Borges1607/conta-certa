import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import type { Role } from '../models/enums';
import { AuthStore } from './auth.store';

/**
 * Guards de rota — Parte 1, §6.
 *
 * São conveniência de navegação, **não segurança**. A autorização real é do
 * servidor, e um `403` de API é sempre tratado ainda que o guard tenha deixado
 * passar.
 */

/** Exige sessão. Sem sessão, vai ao login preservando o destino. */
export const authGuard: CanActivateFn = (_route, state) => {
  const store = inject(AuthStore);
  const router = inject(Router);

  if (store.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Exige um dos perfis informados. */
export function roleGuard(...roles: readonly Role[]): CanActivateFn {
  return (_route, state) => {
    const store = inject(AuthStore);
    const router = inject(Router);

    if (!store.isAuthenticated()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }

    const role = store.role();
    return role && roles.includes(role) ? true : router.createUrlTree(['/403']);
  };
}

/** Usuário autenticado não fica em tela pública de entrada. */
export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const router = inject(Router);

  return store.isAuthenticated() ? router.createUrlTree([store.homePath()]) : true;
};

/**
 * Enquanto `mustChangePassword` for verdadeiro, nenhuma rota autenticada é
 * acessível além da troca de senha — §5 da visão geral.
 */
export const passwordChangeGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const router = inject(Router);

  if (!store.isAuthenticated() || !store.mustChangePassword()) {
    return true;
  }

  return router.createUrlTree(['/conta/senha']);
};
