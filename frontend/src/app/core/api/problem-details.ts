import { HttpErrorResponse } from '@angular/common/http';

/** Erro de campo — §2.4 da spec de integração. */
export interface FieldError {
  field: string;
  message: string;
}

/** `application/problem+json` — RFC 9457, §2.4 da spec. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  code?: string;
  detail?: string;
  instance?: string;
  timestamp?: string;
  traceId?: string;
  fieldErrors?: FieldError[];
}

/** Erro de rede: a requisição nem chegou ao servidor. */
export const NETWORK_ERROR_CODE = 'NETWORK_ERROR';

/** Código de conflito de versão — §3 da spec. */
export const VERSION_CONFLICT_CODE = 'VERSION_CONFLICT';

/**
 * Mensagens padrão por status — Parte 1, §5.1 das specs de frontend.
 *
 * Usadas quando o servidor não manda um `detail` apresentável. `401`, `403`,
 * `404` e `409` são deliberadamente distintas: a §9 da spec de integração exige
 * que o usuário consiga diferenciá-las.
 */
const DEFAULT_MESSAGES: Readonly<Record<number, string>> = {
  0: 'Sem conexão. Verifique sua internet e tente novamente.',
  400: 'Requisição inválida.',
  401: 'Sua sessão expirou. Entre novamente.',
  403: 'Você não tem permissão para esta ação.',
  404: 'Não encontramos o que você procura.',
  409: 'Os dados mudaram desde que você abriu esta tela.',
  410: 'Este link ou tentativa não está mais disponível.',
  413: 'Arquivo acima do tamanho permitido.',
  415: 'Tipo de arquivo não aceito.',
  422: 'Verifique os campos destacados.',
  429: 'Muitas tentativas. Aguarde alguns instantes.',
};

const SERVER_ERROR_MESSAGE = 'Erro no servidor. Tente novamente em instantes.';
const UNKNOWN_ERROR_MESSAGE = 'Algo deu errado. Tente novamente.';

export function defaultMessageForStatus(status: number): string {
  const known = DEFAULT_MESSAGES[status];
  if (known) {
    return known;
  }
  return status >= 500 ? SERVER_ERROR_MESSAGE : UNKNOWN_ERROR_MESSAGE;
}

/**
 * Erro único da aplicação.
 *
 * Toda falha HTTP vira uma instância disto no `errorInterceptor`. Nenhuma
 * feature manipula `HttpErrorResponse` — Parte 1, §5.1.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly fieldErrors: readonly FieldError[];
  readonly traceId?: string;
  readonly problem?: ProblemDetails;

  constructor(init: {
    status: number;
    code: string;
    detail: string;
    fieldErrors?: readonly FieldError[];
    traceId?: string;
    problem?: ProblemDetails;
  }) {
    super(init.detail);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.detail = init.detail;
    this.fieldErrors = init.fieldErrors ?? [];
    this.traceId = init.traceId;
    this.problem = init.problem;
  }

  /** A requisição não chegou ao servidor. */
  get isOffline(): boolean {
    return this.status === 0;
  }

  /** Erro de validação de campo ou de regra de negócio. */
  get isValidation(): boolean {
    return this.status === 422 || this.fieldErrors.length > 0;
  }

  /** Recurso alterado por outra pessoa — exige recarregar, nunca sobrescrever. */
  get isVersionConflict(): boolean {
    return this.status === 409 && this.code === VERSION_CONFLICT_CODE;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  /** Convite, token ou tentativa definitivamente encerrada. */
  get isGone(): boolean {
    return this.status === 410;
  }

  get isTooManyRequests(): boolean {
    return this.status === 429;
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }

  /** Erros de um campo específico, para o formulário destacar. */
  errorsFor(field: string): readonly FieldError[] {
    return this.fieldErrors.filter((e) => e.field === field);
  }

  /**
   * Converte a falha bruta do `HttpClient`. O corpo pode ser `problem+json`,
   * um JSON qualquer ou nada — todos os casos caem em uma mensagem utilizável.
   */
  static fromHttp(response: HttpErrorResponse): ApiError {
    const status = response.status ?? 0;

    if (status === 0) {
      return new ApiError({
        status: 0,
        code: NETWORK_ERROR_CODE,
        detail: defaultMessageForStatus(0),
      });
    }

    const problem = parseProblem(response.error);
    const detail = presentableDetail(problem, status);

    return new ApiError({
      status,
      code: problem?.code ?? `HTTP_${status}`,
      detail,
      fieldErrors: problem?.fieldErrors ?? [],
      traceId: problem?.traceId,
      problem: problem ?? undefined,
    });
  }
}

function parseProblem(body: unknown): ProblemDetails | null {
  if (!body) {
    return null;
  }

  // Com `responseType: 'blob'` o corpo do erro vem como Blob e não é legível
  // de forma síncrona. Nesse caso vale a mensagem padrão do status.
  if (typeof body === 'string') {
    try {
      return JSON.parse(body) as ProblemDetails;
    } catch {
      return null;
    }
  }

  return typeof body === 'object' ? (body as ProblemDetails) : null;
}

/**
 * O `detail` do servidor só é usado quando é apresentável ao usuário final.
 * Stack traces e mensagens técnicas caem na mensagem padrão do status.
 */
function presentableDetail(problem: ProblemDetails | null, status: number): string {
  const detail = problem?.detail?.trim();
  if (!detail) {
    return defaultMessageForStatus(status);
  }

  const looksTechnical =
    detail.length > 300 || detail.includes('\n at ') || /^[a-z]+\.[a-z]+\.[A-Z]\w+(Exception|Error)/.test(detail);

  return looksTechnical ? defaultMessageForStatus(status) : detail;
}
