import { HttpEvent, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, delay, of, throwError } from 'rxjs';

import { environment } from '../environments/environment';
import { adminRoutes } from './handlers/admin';
import { authRoutes } from './handlers/auth';
import { studentRoutes } from './handlers/student';
import { teacherRoutes } from './handlers/teacher';
import { teacherMediaRoutes } from './handlers/teacher-media';
import { MockHttpError, NO_CONTENT, compile, match, notFound, type MockContext } from './router';
import { seedProgressOnce } from './seed-progress';
import './seed';

/**
 * Motor do mock — carregado sob demanda pelo interceptor.
 *
 * Separado de `mock-api.interceptor.ts` de propósito: assim o `db`, a semente e
 * os handlers ficam num chunk que só é baixado quando `useMockApi` é `true`.
 * Em produção, nada disto entra no pacote.
 */

const ROUTES = compile([
  ...authRoutes,
  ...studentRoutes,
  ...teacherRoutes,
  ...teacherMediaRoutes,
  ...adminRoutes,
]);

/** Latência simulada, para os estados de carregamento serem reais. */
const MIN_DELAY_MS = 150;
const MAX_DELAY_MS = 400;

export function handle(request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
  // Progresso de exemplo, na primeira requisição: ranking, conquistas e
  // histórico precisam de dados para provar que funcionam.
  seedProgressOnce();

  const url = new URL(request.url, window.location.origin);
  const path = url.pathname.replace(environment.apiBaseUrl, '');
  const latency = MIN_DELAY_MS + Math.random() * (MAX_DELAY_MS - MIN_DELAY_MS);

  const matched = match(ROUTES, request.method, path);

  if (!matched) {
    // Endpoint ainda não implementado no mock. O 404 é honesto: melhor a tela
    // mostrar "não encontrado" do que fingir um dado que não existe.
    console.warn(`[mock] Sem handler para ${request.method} ${path}`);
    return delayed(
      throwError(() =>
        notFound(`Endpoint não implementado no mock: ${request.method} ${path}`).toResponse(
          request.url,
        ),
      ),
      latency,
    );
  }

  const context: MockContext = {
    request,
    params: matched.params,
    query: url.searchParams,
    body: request.body,
    accessToken: readBearer(request.headers),
  };

  try {
    const result = matched.route.handler(context);

    return delayed(
      of(
        new HttpResponse({
          status: result === NO_CONTENT ? 204 : 200,
          statusText: 'OK',
          url: request.url,
          body: result === NO_CONTENT ? null : result,
          // O `ServerClock` mede o desvio por este header (Parte 1, §7).
          headers: new HttpHeaders({ Date: new Date().toUTCString() }),
        }),
      ),
      latency,
    );
  } catch (error) {
    if (error instanceof MockHttpError) {
      return delayed(
        throwError(() => error.toResponse(request.url)),
        latency,
      );
    }
    throw error;
  }
}

function delayed(
  source: Observable<HttpEvent<unknown>>,
  ms: number,
): Observable<HttpEvent<unknown>> {
  return source.pipe(delay(ms));
}

function readBearer(headers: HttpHeaders): string | null {
  const value = headers.get('Authorization');
  return value?.startsWith('Bearer ') ? value.slice(7) : null;
}
