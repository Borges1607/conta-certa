import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { ALREADY_RETRIED, SKIP_REFRESH, isPublicEndpoint } from '../api/http-context';
import { AuthStore } from '../auth/auth.store';
import { TokenStorage } from '../auth/token-storage';

/**
 * Trata `401` com refresh único e uma repetição — Parte 1, §4.3 e §5.
 *
 * Regras da §2.2 da spec de integração implementadas aqui:
 *
 * - só age em `401`; `403` **nunca** dispara refresh;
 * - rotas públicas não disparam refresh — um `401` de `/auth/login` é
 *   credencial inválida, não sessão expirada;
 * - uma requisição é repetida **no máximo uma vez**;
 * - se o refresh falhar, a sessão local é limpa e o usuário volta ao login.
 *
 * A unicidade da chamada a `/auth/refresh` vem do `AuthStore.refresh()`, que
 * devolve o mesmo observable compartilhado para todos os concorrentes.
 */
export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_REFRESH) || isPublicEndpoint(req.url)) {
    return next(req);
  }

  const store = inject(AuthStore);
  const storage = inject(TokenStorage);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!isUnauthorized(error)) {
        return throwError(() => error);
      }

      // Já tentamos uma vez: insistir viraria laço.
      if (req.context.get(ALREADY_RETRIED)) {
        void store.expireSession();
        return throwError(() => error);
      }

      return store.refresh().pipe(
        switchMap(() => next(retryWithFreshToken(req, storage.accessToken()))),
        catchError(() => {
          // O `AuthStore` já limpou a sessão ao falhar o refresh.
          void store.expireSession();
          return throwError(() => error);
        }),
      );
    }),
  );
};

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}

function retryWithFreshToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  const context = req.context.set(ALREADY_RETRIED, true);
  return token
    ? req.clone({ context, setHeaders: { Authorization: `Bearer ${token}` } })
    : req.clone({ context });
}
