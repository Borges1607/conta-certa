import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  HttpEventType,
  HttpHeaders,
  HttpParams,
} from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, throwError } from 'rxjs';
import { catchError, filter } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import type { Page, PageQuery } from '../models/page';
import { MAX_PAGE_SIZE } from '../models/page';
import { ApiError } from './problem-details';

/** Valores aceitos como parâmetro de query. Nulos e vazios são descartados. */
export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue | readonly QueryValue[]>;

export interface RequestOptions {
  params?: QueryParams;
  context?: HttpContext;
  headers?: Record<string, string>;
}

export interface MutationOptions extends RequestOptions {
  /** Enviado como header `Idempotency-Key` — §6.3 da spec de integração. */
  idempotencyKey?: string;
}

export interface UploadProgress {
  kind: 'progress';
  /** 0 a 100, ou `null` quando o total é desconhecido. */
  percent: number | null;
  loaded: number;
  total?: number;
}

export type UploadEvent<T> = UploadProgress | { kind: 'done'; body: T };

/**
 * Cliente HTTP da aplicação — Parte 1, §3.
 *
 * Concentra montagem de URL e de parâmetros. **Não** trata erro nem
 * autenticação: isso é dos interceptors. É o único lugar do código de
 * aplicação que injeta `HttpClient` (garantido por regra de ESLint).
 */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  get<T>(path: string, options: RequestOptions = {}): Observable<T> {
    return this.http.get<T>(this.url(path), this.httpOptions(options));
  }

  /** Igual a `get`, mas serializa `PageQuery` junto dos demais filtros. */
  getPage<T>(
    path: string,
    query: PageQuery = {},
    options: RequestOptions = {},
  ): Observable<Page<T>> {
    return this.get<Page<T>>(path, {
      ...options,
      params: { ...options.params, ...pageParams(query) },
    });
  }

  post<T>(path: string, body?: unknown, options: MutationOptions = {}): Observable<T> {
    return this.http.post<T>(this.url(path), body ?? {}, this.httpOptions(options));
  }

  patch<T>(path: string, body: unknown, options: MutationOptions = {}): Observable<T> {
    return this.http.patch<T>(this.url(path), body, this.httpOptions(options));
  }

  put<T>(path: string, body: unknown, options: MutationOptions = {}): Observable<T> {
    return this.http.put<T>(this.url(path), body, this.httpOptions(options));
  }

  delete<T>(path: string, options: MutationOptions = {}): Observable<T> {
    return this.http.delete<T>(this.url(path), this.httpOptions(options));
  }

  /**
   * Upload multipart com relatório de progresso real — Parte 2, §4.3.
   * Emite progresso até concluir e então o corpo da resposta.
   */
  postMultipart<T>(
    path: string,
    form: FormData,
    options: MutationOptions = {},
  ): Observable<UploadEvent<T>> {
    return this.http
      .post<T>(this.url(path), form, {
        ...this.httpOptions(options),
        observe: 'events',
        reportProgress: true,
      })
      .pipe(
        filter(
          (event) =>
            event.type === HttpEventType.UploadProgress || event.type === HttpEventType.Response,
        ),
        map((event): UploadEvent<T> => {
          if (event.type === HttpEventType.UploadProgress) {
            return {
              kind: 'progress',
              percent: event.total ? Math.round((event.loaded / event.total) * 100) : null,
              loaded: event.loaded,
              total: event.total,
            };
          }
          return { kind: 'done', body: (event as { body: T }).body };
        }),
      );
  }

  /**
   * Baixa um arquivo privado pelo endpoint autorizado — §6.4 da spec.
   *
   * O blob é o único caminho: nenhuma URL da API vai para `href` ou `src`
   * (Parte 2, §4.3).
   */
  download(path: string, options: RequestOptions = {}): Observable<Blob> {
    return this.http
      .get(this.url(path), {
        ...this.httpOptions(options),
        responseType: 'blob',
      })
      .pipe(
        catchError((error: unknown) =>
          error instanceof HttpErrorResponse
            ? throwError(() => ApiError.fromHttp(error))
            : throwError(() => error),
        ),
      );
  }

  private url(path: string): string {
    if (/^https?:\/\//i.test(path)) {
      return path;
    }
    return `${this.baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
  }

  private httpOptions(options: MutationOptions) {
    return {
      params: buildParams(options.params),
      context: options.context,
      headers: buildHeaders(options),
    };
  }
}

/** Descarta `null`, `undefined` e string vazia; expande arrays. */
export function buildParams(params?: QueryParams): HttpParams {
  let result = new HttpParams();
  if (!params) {
    return result;
  }

  for (const [key, value] of Object.entries(params)) {
    if (Array.isArray(value)) {
      for (const item of value) {
        if (isUsable(item)) {
          result = result.append(key, String(item));
        }
      }
    } else if (isUsable(value as QueryValue)) {
      result = result.set(key, String(value));
    }
  }

  return result;
}

function isUsable(value: QueryValue): boolean {
  return value !== null && value !== undefined && value !== '';
}

function buildHeaders(options: MutationOptions): HttpHeaders | undefined {
  const entries = { ...options.headers };
  if (options.idempotencyKey) {
    entries['Idempotency-Key'] = options.idempotencyKey;
  }
  return Object.keys(entries).length > 0 ? new HttpHeaders(entries) : undefined;
}

/** Aplica os padrões e o teto de `size` da §2.3 da spec. */
export function pageParams(query: PageQuery): QueryParams {
  return {
    page: query.page,
    size: query.size === undefined ? undefined : Math.min(query.size, MAX_PAGE_SIZE),
    sort: query.sort,
  };
}
