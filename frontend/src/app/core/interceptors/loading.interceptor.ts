import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { SILENT } from '../api/http-context';

/**
 * Contador global de requisições em andamento — Parte 1, §5.2.
 *
 * Alimenta a barra de progresso do shell. Requisições marcadas com `SILENT`
 * não contam: polling, refresh e atualização em segundo plano não devem
 * acender a barra.
 */
@Injectable({ providedIn: 'root' })
export class LoadingIndicator {
  private readonly count = signal(0);

  readonly pendingRequests = this.count.asReadonly();
  readonly isLoading = computed(() => this.count() > 0);

  increment(): void {
    this.count.update((n) => n + 1);
  }

  decrement(): void {
    this.count.update((n) => Math.max(0, n - 1));
  }
}

export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SILENT)) {
    return next(req);
  }

  const indicator = inject(LoadingIndicator);
  indicator.increment();

  return next(req).pipe(finalize(() => indicator.decrement()));
};
