import { HttpErrorResponse } from '@angular/common/http';

import { ApiError, VERSION_CONFLICT_CODE, defaultMessageForStatus } from './problem-details';

describe('ApiError', () => {
  const httpError = (status: number, body: unknown) =>
    new HttpErrorResponse({ status, statusText: 'x', error: body, url: '/api/v1/rooms' });

  it('preserva fieldErrors do problem+json', () => {
    const error = ApiError.fromHttp(
      httpError(422, {
        type: 'https://api.contacerta/errors/validation',
        title: 'Validation failed',
        status: 422,
        code: 'VALIDATION_ERROR',
        detail: 'One or more fields are invalid.',
        traceId: '01K',
        fieldErrors: [
          { field: 'name', message: 'must not be blank' },
          { field: 'cnpj', message: 'invalid' },
        ],
      }),
    );

    expect(error.status).toBe(422);
    expect(error.code).toBe('VALIDATION_ERROR');
    expect(error.traceId).toBe('01K');
    expect(error.isValidation).toBe(true);
    expect(error.fieldErrors.length).toBe(2);
    expect(error.errorsFor('name')[0].message).toBe('must not be blank');
    expect(error.errorsFor('inexistente')).toEqual([]);
  });

  it('trata erro de rede como offline', () => {
    const error = ApiError.fromHttp(
      new HttpErrorResponse({ status: 0, statusText: 'Unknown Error', error: new ProgressEvent('error') }),
    );

    expect(error.isOffline).toBe(true);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.detail).toContain('Sem conexão');
  });

  it('identifica conflito de versão', () => {
    const conflict = ApiError.fromHttp(httpError(409, { code: VERSION_CONFLICT_CODE }));
    const outroConflito = ApiError.fromHttp(httpError(409, { code: 'DUPLICATE_EMAIL' }));

    expect(conflict.isVersionConflict).toBe(true);
    expect(outroConflito.isVersionConflict).toBe(false);
    expect(outroConflito.status).toBe(409);
  });

  it('dá mensagens distintas para 401, 403, 404 e 409', () => {
    // A §9 da spec exige que o usuário consiga diferenciar estes quatro.
    const mensagens = [401, 403, 404, 409].map((s) => defaultMessageForStatus(s));
    expect(new Set(mensagens).size).toBe(4);
  });

  it('usa a mensagem padrão quando o servidor não manda detail', () => {
    expect(ApiError.fromHttp(httpError(403, null)).detail).toBe(defaultMessageForStatus(403));
    expect(ApiError.fromHttp(httpError(500, {})).detail).toContain('servidor');
  });

  it('descarta detail técnico em favor da mensagem padrão', () => {
    const error = ApiError.fromHttp(
      httpError(500, { detail: 'java.lang.NullPointerException: cannot invoke getId()' }),
    );
    expect(error.detail).toBe(defaultMessageForStatus(500));
  });

  it('aceita corpo problem+json entregue como string', () => {
    const error = ApiError.fromHttp(
      httpError(410, JSON.stringify({ code: 'TOKEN_EXPIRED', detail: 'Convite expirado.' })),
    );
    expect(error.code).toBe('TOKEN_EXPIRED');
    expect(error.detail).toBe('Convite expirado.');
    expect(error.isGone).toBe(true);
  });
});
