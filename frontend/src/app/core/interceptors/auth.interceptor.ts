import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { SKIP_AUTH, isPublicEndpoint } from '../api/http-context';
import { TokenStorage } from '../auth/token-storage';

/**
 * Anexa `Authorization: Bearer` — Parte 1, §5.
 *
 * Este é o **único** lugar do projeto que escreve esse header.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_AUTH) || isPublicEndpoint(req.url)) {
    return next(req);
  }

  const token = inject(TokenStorage).accessToken();
  if (!token) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
