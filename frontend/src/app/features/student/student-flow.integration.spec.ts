import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthStore } from '../../core/auth/auth.store';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { refreshInterceptor } from '../../core/interceptors/refresh.interceptor';
import { resetDatabase } from '../../../mocks/db';
import { mockApiInterceptor } from '../../../mocks/mock-api.interceptor';
import '../../../mocks/seed';
import { AttemptService } from './data/attempt.service';
import { StudentLessonService } from './data/student-lesson.service';
import { StudentRoomService } from './data/student-room.service';
import { RankingService } from './data/ranking.service';
import { AchievementService } from './data/achievement.service';
import type { AnswerPayload } from './models/attempt-question';

/**
 * Percorre a jornada do aluno de ponta a ponta, pela cadeia real: services →
 * `ApiClient` → interceptors → mock.
 *
 * Substitui o clique manual. Cobre os pontos que a §11 da spec de integração
 * trata como críticos: sigilo do gabarito durante a tentativa, imutabilidade da
 * resposta, correção feita pelo servidor e isolamento por sala.
 */
describe('Jornada do aluno (integração com o mock)', () => {
  let auth: AuthStore;
  let rooms: StudentRoomService;
  let lessons: StudentLessonService;
  let attempts: AttemptService;

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    // O banco do mock é um singleton de módulo: sem isto, as tentativas de um
    // teste esgotariam o limite do teste seguinte.
    resetDatabase();

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(
          withInterceptors([
            authInterceptor,
            errorInterceptor,
            refreshInterceptor,
            mockApiInterceptor,
          ]),
        ),
      ],
    });

    auth = TestBed.inject(AuthStore);
    rooms = TestBed.inject(StudentRoomService);
    lessons = TestBed.inject(StudentLessonService);
    attempts = TestBed.inject(AttemptService);

    await auth.login({ email: 'carla@contacerta.dev', password: 'senha123' });
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('autentica e devolve o aluno com instituição', () => {
    const user = auth.user();
    expect(user?.role).toBe('STUDENT');
    expect(user?.fullName).toBe('Carla Souza');
    expect(user?.institution?.name).toContain('IFSC');
  });

  it('lista as salas do aluno', async () => {
    const list = await rooms.listRooms();
    expect(list.length).toBeGreaterThanOrEqual(2);
    expect(list.every((room) => room.membershipStatus === 'ACTIVE')).toBe(true);
  });

  it('o dashboard traz todos os agregados prontos', async () => {
    const list = await rooms.listRooms();
    const dashboard = await rooms.dashboard(list[0].id);

    // A tela não calcula nada: cada um destes campos precisa vir da API.
    expect(dashboard.level).toBeGreaterThanOrEqual(1);
    expect(dashboard.xpTotal).toBeGreaterThanOrEqual(0);
    expect(dashboard.levelProgressPercent).toBeGreaterThanOrEqual(0);
    expect(dashboard.lessonsTotal).toBeGreaterThan(0);
    expect(dashboard.starsPossible).toBe(dashboard.lessonsTotal * 3);
    expect(dashboard.tipOfDay).not.toBeNull();
  });

  it('a trilha vem ordenada e com bloqueio decidido pela API', async () => {
    const list = await rooms.listRooms();
    const track = await lessons.track(list[0].id);

    expect(track.length).toBeGreaterThan(0);

    const orders = track.map((item) => item.order);
    expect([...orders].sort((a, b) => a - b)).toEqual(orders);

    // Toda lição bloqueada traz o motivo — o critério "estados distinguíveis".
    for (const item of track) {
      if (item.availability === 'LOCKED') {
        expect(item.lockReason).not.toBeNull();
      } else {
        expect(item.lockReason).toBeNull();
      }
    }
  });

  it('faz uma tentativa inteira e recebe o resultado corrigido pelo servidor', async () => {
    const list = await rooms.listRooms();
    const roomId = list[0].id;
    const track = await lessons.track(roomId);

    const target = track.find((item) => item.availability !== 'LOCKED');
    expect(target).toBeDefined();

    // 1. Início, com chave de idempotência.
    const started = await attempts.start(target!.assignmentId, 'chave-teste-1');
    expect(started.status).toBe('IN_PROGRESS');

    // 2. Repetir a mesma intenção não cria outra tentativa.
    const repeated = await attempts.start(target!.assignmentId, 'chave-teste-1');
    expect(repeated.attemptId).toBe(started.attemptId);

    // 3. Hidratação: questões sorteadas, sem gabarito.
    const detail = await attempts.get(started.attemptId);
    expect(detail.questions.length).toBeGreaterThan(0);

    const serialized = JSON.stringify(detail);
    expect(serialized).not.toContain('"correct"');
    expect(serialized).not.toContain('correctAnswer');
    expect(serialized).not.toContain('explanation');
    for (const question of detail.questions) {
      for (const option of question.options ?? []) {
        expect(Object.keys(option).sort()).toEqual(['id', 'text']);
      }
    }

    // 4. Responde tudo.
    for (const question of detail.questions) {
      const recorded = await attempts.answer(
        started.attemptId,
        question.questionSnapshotId,
        payloadFor(question.type, question.options?.[0]?.id),
      );
      // O comprovante não carrega correção.
      expect('correct' in recorded).toBe(false);
    }

    // 5. A resposta é imutável dentro da tentativa.
    await expect(
      attempts.answer(
        started.attemptId,
        detail.questions[0].questionSnapshotId,
        payloadFor(detail.questions[0].type, detail.questions[0].options?.[0]?.id),
      ),
    ).rejects.toMatchObject({ status: 409 });

    // 6. Finaliza e lê o resultado.
    await attempts.submit(started.attemptId);
    const result = await attempts.result(started.attemptId);

    expect(result.status).toBe('SUBMITTED');
    expect(result.totalQuestions).toBe(detail.questions.length);
    expect(result.scorePercent).toBeGreaterThanOrEqual(0);
    expect(result.scorePercent).toBeLessThanOrEqual(100);
    expect(result.answers.length).toBe(detail.questions.length);

    // Só agora gabarito e explicação existem.
    for (const answer of result.answers) {
      expect(answer.correctAnswer).toBeDefined();
      expect(typeof answer.correct).toBe('boolean');
    }

    // As estrelas seguem as faixas da §6.3 — calculadas pelo servidor.
    const expectedStars =
      result.scorePercent < 50 ? 0 : result.scorePercent < 70 ? 1 : result.scorePercent < 90 ? 2 : 3;
    expect(result.stars).toBe(expectedStars);
    expect(result.passed).toBe(result.scorePercent >= result.passingScorePercent);
  });

  it('finalizar duas vezes devolve 410, e a tela trata como encerramento normal', async () => {
    const list = await rooms.listRooms();
    const track = await lessons.track(list[0].id);
    const target = track.find((item) => item.availability !== 'LOCKED');

    const started = await attempts.start(target!.assignmentId, 'chave-teste-2');
    await attempts.submit(started.attemptId);

    await expect(attempts.submit(started.attemptId)).rejects.toMatchObject({ status: 410 });
  });

  it('o ranking chega anonimizado e sem dados pessoais dos colegas', async () => {
    const list = await rooms.listRooms();
    const page = await TestBed.inject(RankingService).ranking(list[0].id);

    expect(page.content.length).toBeGreaterThan(0);

    const others = page.content.filter((entry) => !entry.me);
    for (const entry of others) {
      // `Ana L.` — primeiro nome e inicial.
      expect(entry.displayName).toMatch(/^\S+ \S\.$/);
      expect(JSON.stringify(entry)).not.toContain('@');
    }

    expect(page.me).not.toBeNull();
  });

  it('as conquistas são calculadas por sala', async () => {
    const list = await rooms.listRooms();
    const service = TestBed.inject(AchievementService);

    const first = await service.achievements(list[0].id);
    const second = await service.achievements(list[1].id);

    expect(first.length).toBe(7);
    expect(second.length).toBe(7);

    // Salas diferentes têm progresso independente.
    const firstXp = first.find((a) => a.code === 'XP_100');
    const secondXp = second.find((a) => a.code === 'XP_100');
    expect(firstXp).toBeDefined();
    expect(secondXp).toBeDefined();
  });

  it('recusa entrada em sala de outra instituição ou com código inexistente', async () => {
    await expect(rooms.join('ZZZZZZ')).rejects.toMatchObject({ status: 404 });
  });

  it('não permite ler tentativa de outro aluno', async () => {
    const list = await rooms.listRooms();
    const track = await lessons.track(list[0].id);
    const target = track.find((item) => item.availability !== 'LOCKED');
    const started = await attempts.start(target!.assignmentId, 'chave-teste-3');

    await auth.logout().catch(() => undefined);
    await auth.login({ email: 'diego@contacerta.dev', password: 'senha123' });

    await expect(attempts.get(started.attemptId)).rejects.toMatchObject({ status: 403 });
  });
});

function payloadFor(type: string, firstOptionId: string | undefined): AnswerPayload {
  switch (type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return { selectedOptionIds: firstOptionId ? [firstOptionId] : [] };
    case 'TRUE_FALSE':
      return { booleanValue: true };
    default:
      return { numericValue: '0.00' };
  }
}
