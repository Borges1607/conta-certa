import { HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Observable, from, switchMap } from 'rxjs';

import { environment } from '../environments/environment';

/**
 * Interceptor do mock — Parte 7, §1.
 *
 * É o **último** da cadeia, no lugar do backend: recebe a requisição já com o
 * header `Authorization` e responde no formato exato do contrato. Nenhum
 * service, componente ou store sabe que ele existe.
 *
 * Este arquivo é deliberadamente minúsculo e **não** importa o motor de forma
 * estática. O `import()` dinâmico coloca o mock inteiro — banco, semente e
 * handlers — num chunk separado, que só é baixado quando `useMockApi` é
 * `true`. Em produção, o custo é este arquivo e mais nada.
 */

type Engine = typeof import('./engine');

let engine: Promise<Engine> | null = null;

function loadEngine(): Promise<Engine> {
  engine ??= import('./engine');
  return engine;
}

export const mockApiInterceptor: HttpInterceptorFn = (request, next) => {
  if (!environment.useMockApi) {
    return next(request);
  }
  return from(loadEngine()).pipe(
    switchMap((module) => module.handle(request as HttpRequest<unknown>) as Observable<HttpEvent<unknown>>),
  );
};
