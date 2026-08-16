import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ApiClient, buildParams, pageParams } from './api-client';

describe('ApiClient', () => {
  let api: ApiClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(ApiClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('prefixa a base da API', () => {
    api.get('/student/rooms').subscribe();
    http.expectOne('/api/v1/student/rooms').flush([]);
  });

  it('omite parâmetros nulos, indefinidos e vazios', () => {
    api
      .get('/teacher/lessons', {
        params: { status: 'PUBLISHED', search: '', lessonId: null, roomId: undefined, page: 0 },
      })
      .subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/teacher/lessons');
    expect(req.request.params.get('status')).toBe('PUBLISHED');
    expect(req.request.params.has('search')).toBe(false);
    expect(req.request.params.has('lessonId')).toBe(false);
    expect(req.request.params.has('roomId')).toBe(false);
    // Zero é um valor válido e precisa sobreviver.
    expect(req.request.params.get('page')).toBe('0');
    req.flush({});
  });

  it('monta paginação e ordenação', () => {
    api.getPage('/teacher/rooms', { page: 2, size: 50, sort: 'name,asc' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/teacher/rooms');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('sort')).toBe('name,asc');
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('respeita o teto de 100 em size', () => {
    expect(pageParams({ size: 500 })['size']).toBe(100);
    expect(pageParams({})['size']).toBeUndefined();
  });

  it('envia Idempotency-Key quando informado', () => {
    api.post('/student/room-lessons/abc/attempts', {}, { idempotencyKey: 'chave-1' }).subscribe();

    const req = http.expectOne((r) => r.url.includes('/attempts'));
    expect(req.request.headers.get('Idempotency-Key')).toBe('chave-1');
    req.flush({});
  });

  it('expande arrays em parâmetros repetidos', () => {
    const params = buildParams({ topic: ['juros', 'descontos', null] });
    expect(params.getAll('topic')).toEqual(['juros', 'descontos']);
  });

  it('baixa arquivo privado como blob', () => {
    let received: Blob | undefined;
    api.download('/files/abc/download').subscribe((b) => (received = b));

    const req = http.expectOne('/api/v1/files/abc/download');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']));

    expect(received).toBeInstanceOf(Blob);
  });
});
