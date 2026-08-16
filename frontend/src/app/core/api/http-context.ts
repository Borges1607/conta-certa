import { HttpContext, HttpContextToken } from '@angular/common/http';

/**
 * Sinalizadores que a chamada usa para conversar com os interceptors.
 * Ver Parte 1, §5.
 */

/** Não anexar `Authorization`. Rotas públicas. */
export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);

/** Não tentar refresh em `401`. Usado pelo próprio `/auth/refresh`. */
export const SKIP_REFRESH = new HttpContextToken<boolean>(() => false);

/** Não contar na barra de progresso global. Polling e atualização em segundo plano. */
export const SILENT = new HttpContextToken<boolean>(() => false);

/** Marca interna: esta requisição já foi repetida uma vez após refresh. */
export const ALREADY_RETRIED = new HttpContextToken<boolean>(() => false);

/** Contexto de uma chamada pública: sem token e sem refresh. */
export function publicContext(): HttpContext {
  return new HttpContext().set(SKIP_AUTH, true).set(SKIP_REFRESH, true);
}

/** Contexto de uma chamada que não deve acender a barra de progresso. */
export function silentContext(base?: HttpContext): HttpContext {
  return (base ?? new HttpContext()).set(SILENT, true);
}

/**
 * Rotas públicas da API — §4.1 da spec de integração.
 *
 * Um `401` vindo daqui nunca dispara refresh: significa credencial inválida,
 * não sessão expirada.
 */
const PUBLIC_PATHS: readonly string[] = [
  '/auth/login',
  '/auth/refresh',
  '/auth/student-registration',
  '/auth/verify-email',
  '/auth/resend-verification',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/accept-teacher-invite',
  '/institutions/options',
];

export function isPublicEndpoint(url: string): boolean {
  const path = pathOf(url);
  return PUBLIC_PATHS.some((p) => path.includes(p));
}

function pathOf(url: string): string {
  try {
    return new URL(url, 'http://local').pathname;
  } catch {
    return url;
  }
}
