import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ApiClient } from '../api/api-client';
import { ApiError } from '../api/problem-details';
import { AuthStore } from '../auth/auth.store';
import { TokenStorage } from '../auth/token-storage';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';
import { refreshInterceptor } from './refresh.interceptor';

/**
 * Protege o critério da §11 da spec de integração: "o cliente trata refresh
 * concorrente com uma única operação em andamento".
 */
describe('refreshInterceptor', () => {
  let http: HttpTestingController;
  let api: ApiClient;
  let storage: TokenStorage;

  const tokens = (suffix: string) => ({
    accessToken: `access-${suffix}`,
    refreshToken: `refresh-${suffix}`,
    tokenType: 'Bearer' as const,
    accessExpiresIn: 900,
    refreshExpiresIn: 604_800,
  });

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(
          withInterceptors([authInterceptor, errorInterceptor, refreshInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(ApiClient);
    storage = TestBed.inject(TokenStorage);
    TestBed.inject(AuthStore);

    storage.saveTokens(tokens('v1'));
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('dispara um único refresh para três 401 simultâneos e repete as três', () => {
    const received: string[] = [];
    api.get<string>('/a').subscribe((v) => received.push(v));
    api.get<string>('/b').subscribe((v) => received.push(v));
    api.get<string>('/c').subscribe((v) => received.push(v));

    const first = http.match((r) => /\/(a|b|c)$/.test(r.url));
    expect(first.length).toBe(3);
    for (const req of first) {
      expect(req.request.headers.get('Authorization')).toBe('Bearer access-v1');
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    }

    // O ponto do teste: uma única chamada a /auth/refresh.
    const refreshCalls = http.match((r) => r.url.includes('/auth/refresh'));
    expect(refreshCalls.length).toBe(1);
    refreshCalls[0].flush(tokens('v2'));

    const retried = http.match((r) => /\/(a|b|c)$/.test(r.url));
    expect(retried.length).toBe(3);
    for (const req of retried) {
      expect(req.request.headers.get('Authorization')).toBe('Bearer access-v2');
      req.flush('ok');
    }

    expect(received).toEqual(['ok', 'ok', 'ok']);
  });

  it('limpa a sessão quando o refresh falha', () => {
    let failure: ApiError | undefined;
    api.get('/a').subscribe({ error: (e: ApiError) => (failure = e) });

    http.expectOne((r) => r.url.endsWith('/a')).flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    http
      .expectOne((r) => r.url.includes('/auth/refresh'))
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(storage.refreshToken()).toBeNull();
    expect(storage.accessToken()).toBeNull();
    expect(failure).toBeInstanceOf(ApiError);
    expect(failure?.status).toBe(401);
  });

  it('não dispara refresh em 403', () => {
    let failure: ApiError | undefined;
    api.get('/a').subscribe({ error: (e: ApiError) => (failure = e) });

    http
      .expectOne((r) => r.url.endsWith('/a'))
      .flush(null, { status: 403, statusText: 'Forbidden' });

    http.expectNone((r) => r.url.includes('/auth/refresh'));
    expect(failure?.status).toBe(403);
    // A sessão continua: 403 é falta de permissão, não sessão expirada.
    expect(storage.refreshToken()).toBe('refresh-v1');
  });

  it('não entra em laço quando o 401 se repete após o refresh', () => {
    let failure: ApiError | undefined;
    api.get('/a').subscribe({ error: (e: ApiError) => (failure = e) });

    http
      .expectOne((r) => r.url.endsWith('/a'))
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    http.expectOne((r) => r.url.includes('/auth/refresh')).flush(tokens('v2'));

    // A repetição também falha: nada de um segundo refresh.
    http
      .expectOne((r) => r.url.endsWith('/a'))
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    http.expectNone((r) => r.url.includes('/auth/refresh'));
    expect(failure?.status).toBe(401);
  });

  it('não dispara refresh para um 401 de rota pública', async () => {
    // Um 401 de /auth/login é credencial inválida, não sessão expirada.
    const attempt = TestBed.inject(AuthStore)
      .login({ email: 'a@b.com', password: 'errada12' })
      .then(() => null)
      .catch((e: unknown) => e as ApiError);

    http
      .expectOne((r) => r.url.includes('/auth/login'))
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    http.expectNone((r) => r.url.includes('/auth/refresh'));

    const failure = await attempt;
    expect(failure).toBeInstanceOf(ApiError);
    expect(failure?.status).toBe(401);
  });

  it('não anexa Authorization em rota pública', () => {
    TestBed.inject(AuthStore)
      .login({ email: 'a@b.com', password: 'senha123' })
      .catch(() => undefined);

    const req = http.expectOne((r) => r.url.includes('/auth/login'));
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(null, { status: 401, statusText: 'Unauthorized' });
  });
});
