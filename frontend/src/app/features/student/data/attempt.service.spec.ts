import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AttemptService } from './attempt.service';

/**
 * Protege o critério mais forte da §11 da spec de integração: "nenhum gabarito
 * aparece antes de a tentativa terminar".
 *
 * A API devolve `correct` ao registrar uma resposta. Este teste garante que o
 * campo morre na borda HTTP e nunca entra no estado da aplicação.
 */
describe('AttemptService — sigilo do gabarito', () => {
  let service: AttemptService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AttemptService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('descarta o campo `correct` da resposta de registro', async () => {
    const promise = service.answer('att-1', 'snap-1', { selectedOptionIds: ['o1'] });

    const req = http.expectOne('/api/v1/student/attempts/att-1/answers/snap-1');
    expect(req.request.method).toBe('PUT');
    req.flush({ questionSnapshotId: 'snap-1', answeredAt: '2026-08-15T19:30:00Z', correct: true });

    const recorded = await promise;

    // O comprovante tem o que a tela precisa — e nada além.
    expect(recorded.questionSnapshotId).toBe('snap-1');
    expect(recorded.answeredAt).toBe('2026-08-15T19:30:00Z');
    expect(Object.keys(recorded).sort()).toEqual(['answer', 'answeredAt', 'questionSnapshotId']);
    expect(JSON.stringify(recorded)).not.toContain('correct');
  });

  it('descarta `correct` mesmo quando é false', async () => {
    const promise = service.answer('att-1', 'snap-2', { booleanValue: false });

    http
      .expectOne('/api/v1/student/attempts/att-1/answers/snap-2')
      .flush({ questionSnapshotId: 'snap-2', answeredAt: '2026-08-15T19:31:00Z', correct: false });

    const recorded = await promise;
    expect('correct' in recorded).toBe(false);
  });

  it('ignora campos novos que a API venha a acrescentar', async () => {
    const promise = service.answer('att-1', 'snap-3', { numericValue: '100.50' });

    http.expectOne('/api/v1/student/attempts/att-1/answers/snap-3').flush({
      questionSnapshotId: 'snap-3',
      answeredAt: '2026-08-15T19:32:00Z',
      correct: true,
      // Campos hipotéticos de uma versão futura da API.
      correctAnswer: { numericValue: '99.00' },
      explanation: 'O valor correto é 99.',
      scoreSoFar: 3,
    });

    const recorded = await promise;

    // A conversão constrói um objeto novo, então nada disto passa.
    expect(JSON.stringify(recorded)).not.toContain('correctAnswer');
    expect(JSON.stringify(recorded)).not.toContain('explanation');
    expect(JSON.stringify(recorded)).not.toContain('scoreSoFar');
  });

  it('envia o valor numérico como string, preservando as casas decimais', async () => {
    void service.answer('att-1', 'snap-4', { numericValue: '100.50' });

    const req = http.expectOne('/api/v1/student/attempts/att-1/answers/snap-4');
    expect(req.request.body).toEqual({ numericValue: '100.50' });
    expect(typeof (req.request.body as { numericValue: unknown }).numericValue).toBe('string');
    req.flush({ questionSnapshotId: 'snap-4', answeredAt: '2026-08-15T19:33:00Z', correct: true });
  });

  it('exige Idempotency-Key ao iniciar uma tentativa', async () => {
    void service.start('assign-1', 'chave-unica');

    const req = http.expectOne('/api/v1/student/room-lessons/assign-1/attempts');
    expect(req.request.headers.get('Idempotency-Key')).toBe('chave-unica');
    req.flush({ attemptId: 'a1', status: 'IN_PROGRESS', startedAt: '', expiresAt: null });
  });
});
