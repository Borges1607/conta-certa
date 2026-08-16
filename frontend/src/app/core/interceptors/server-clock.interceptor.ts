import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { ServerClock } from '../util/server-clock';

/**
 * Mede o desvio entre o relógio local e o do servidor — Parte 1, §7.
 *
 * `Date` é um header de resposta seguro por CORS, então está sempre legível.
 */
export const serverClockInterceptor: HttpInterceptorFn = (req, next) => {
  const clock = inject(ServerClock);
  const startedAt = Date.now();

  return next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        clock.registerResponse(event.headers.get('Date'), startedAt);
      }
    }),
  );
};
