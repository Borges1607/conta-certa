import { HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';

import type { ProblemDetails } from '../app/core/api/problem-details';

/**
 * Roteador do mock — Parte 7, §1.
 *
 * O mock ocupa o lugar do backend: recebe a requisição já com o header
 * `Authorization` e responde no formato exato do contrato. Nenhum service da
 * aplicação sabe que ele existe.
 */

export interface MockContext {
  readonly request: HttpRequest<unknown>;
  /** Parâmetros extraídos do caminho, ex.: `{ roomId: 'uuid' }`. */
  readonly params: Record<string, string>;
  readonly query: URLSearchParams;
  readonly body: unknown;
  /** Token do header `Authorization`, sem o prefixo `Bearer`. */
  readonly accessToken: string | null;
}

export type MockHandler = (context: MockContext) => unknown;

export interface MockRoute {
  method: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  /** Caminho relativo à base, com `:param`. Ex.: `/student/rooms/:roomId`. */
  path: string;
  handler: MockHandler;
}

/** Erro no formato `application/problem+json` — §2.4 da spec de integração. */
export class MockHttpError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ProblemDetails,
  ) {
    super(problem.detail ?? problem.title ?? 'Erro');
    this.name = 'MockHttpError';
  }

  toResponse(url: string): HttpErrorResponse {
    return new HttpErrorResponse({
      status: this.status,
      statusText: this.problem.title ?? 'Error',
      url,
      error: {
        type: `https://api.contacerta/errors/${this.problem.code?.toLowerCase() ?? 'error'}`,
        title: this.problem.title ?? 'Error',
        status: this.status,
        instance: url,
        timestamp: new Date().toISOString(),
        traceId: `mock-${Math.abs(hash(url)).toString(36)}`,
        ...this.problem,
      },
    });
  }
}

export function problem(
  status: number,
  code: string,
  detail: string,
  extra: Partial<ProblemDetails> = {},
): MockHttpError {
  return new MockHttpError(status, { code, detail, title: code, status, ...extra });
}

export const badRequest = (detail: string) => problem(400, 'BAD_REQUEST', detail);
export const unauthorized = (detail = 'Token ausente, expirado ou inválido.') =>
  problem(401, 'UNAUTHORIZED', detail);
export const forbidden = (detail = 'Você não tem permissão para esta ação.') =>
  problem(403, 'FORBIDDEN', detail);
export const notFound = (detail = 'Recurso não encontrado.') => problem(404, 'NOT_FOUND', detail);
export const conflict = (code: string, detail: string) => problem(409, code, detail);
export const gone = (detail = 'Este link não está mais disponível.') =>
  problem(410, 'GONE', detail);
export const versionConflict = () =>
  problem(409, 'VERSION_CONFLICT', 'Este recurso foi alterado por outra pessoa.');
export const unprocessable = (detail: string, fieldErrors: { field: string; message: string }[] = []) =>
  problem(422, 'VALIDATION_ERROR', detail, { fieldErrors });
export const tooManyRequests = () =>
  problem(429, 'TOO_MANY_REQUESTS', 'Muitas tentativas. Aguarde alguns instantes.');

/** Resposta sem corpo, para `204 No Content`. */
export const NO_CONTENT = Symbol('no-content');

interface CompiledRoute extends MockRoute {
  readonly segments: readonly string[];
}

export function compile(routes: readonly MockRoute[]): CompiledRoute[] {
  return routes.map((route) => ({ ...route, segments: route.path.split('/').filter(Boolean) }));
}

export function match(
  routes: readonly CompiledRoute[],
  method: string,
  path: string,
): { route: CompiledRoute; params: Record<string, string> } | null {
  const parts = path.split('/').filter(Boolean);

  for (const route of routes) {
    if (route.method !== method || route.segments.length !== parts.length) {
      continue;
    }

    const params: Record<string, string> = {};
    let matched = true;

    for (let i = 0; i < route.segments.length; i++) {
      const segment = route.segments[i];
      if (segment.startsWith(':')) {
        params[segment.slice(1)] = decodeURIComponent(parts[i]);
      } else if (segment !== parts[i]) {
        matched = false;
        break;
      }
    }

    if (matched) {
      return { route, params };
    }
  }

  return null;
}

export function jsonResponse(body: unknown, status = 200, url = ''): HttpResponse<unknown> {
  return new HttpResponse({
    status,
    statusText: 'OK',
    url,
    body: body === NO_CONTENT ? null : body,
    // O `ServerClock` mede o desvio por este header — o mock precisa mandá-lo
    // para o cronômetro se comportar como em produção (Parte 1, §7).
    headers: undefined,
  });
}

function hash(value: string): number {
  let result = 0;
  for (let i = 0; i < value.length; i++) {
    result = (result << 5) - result + value.charCodeAt(i);
    result |= 0;
  }
  return result;
}
