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
import { LessonAssignmentService } from './data/lesson-assignment.service';
import { LessonService } from './data/lesson.service';
import { QuestionService } from './data/question.service';
import { ReportService } from './data/report.service';
import { TeacherDashboardService } from './data/teacher-dashboard.service';
import { TeacherRoomService } from './data/teacher-room.service';
import { TeacherStudentService } from './data/teacher-student.service';

/**
 * Percorre a jornada do professor pela cadeia real: services → `ApiClient` →
 * interceptors → mock.
 *
 * Cobre o que a Parte 5 trata como crítico: `version` em toda alteração,
 * `409` sem sobrescrita, `null` como "sem limite", fuso em UTC e a recusa de
 * publicar lição sem questões.
 */
describe('Jornada do professor (integração com o mock)', () => {
  let auth: AuthStore;
  let rooms: TeacherRoomService;
  let lessons: LessonService;
  let questions: QuestionService;
  let assignments: LessonAssignmentService;

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
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
    rooms = TestBed.inject(TeacherRoomService);
    lessons = TestBed.inject(LessonService);
    questions = TestBed.inject(QuestionService);
    assignments = TestBed.inject(LessonAssignmentService);

    await auth.login({ email: 'ana@contacerta.dev', password: 'senha123' });
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('autentica como professor', () => {
    expect(auth.user()?.role).toBe('TEACHER');
  });

  it('o painel traz os agregados prontos', async () => {
    const data = await TestBed.inject(TeacherDashboardService).load();
    expect(data.roomCount).toBeGreaterThan(0);
    expect(data.publishedLessonCount).toBeGreaterThan(0);
    expect(data.recentRooms.length).toBeGreaterThan(0);
  });

  it('cria uma sala sem escolher instituição — ela vem do professor', async () => {
    const created = await rooms.create({
      name: 'Turma de teste',
      description: 'Criada pelo teste',
      grade: 'HIGH_SCHOOL_1',
      contentTopics: ['Porcentagem'],
      passingScorePercent: 60,
    });

    expect(created.name).toBe('Turma de teste');
    expect(created.joinCode).toMatch(/^[A-Z0-9]{6}$/);
    // A instituição é derivada do professor autenticado (§7.1 da spec).
    expect(created.institution.id).toBe(auth.user()?.institution?.id);
    expect(created.version).toBe(1);
  });

  it('exige version na edição e recusa versão desatualizada sem sobrescrever', async () => {
    const page = await rooms.list();
    const room = page.content[0];

    const updated = await rooms.update(room.id, { version: room.version, name: 'Nome novo' });
    expect(updated.name).toBe('Nome novo');
    expect(updated.version).toBe(room.version + 1);

    // Reenviar a versão antiga é exatamente o cenário de duas abas.
    await expect(
      rooms.update(room.id, { version: room.version, name: 'Sobrescrita indevida' }),
    ).rejects.toMatchObject({ status: 409, code: 'VERSION_CONFLICT' });

    // E o nome anterior permanece: nada foi sobrescrito.
    const fresh = await rooms.get(room.id);
    expect(fresh.name).toBe('Nome novo');
  });

  it('regenerar o código não mexe nas matrículas', async () => {
    const page = await rooms.list();
    const room = page.content.find((r) => r.studentCount > 0);
    expect(room).toBeDefined();

    const before = room!.studentCount;
    const updated = await rooms.regenerateCode(room!.id);

    expect(updated.joinCode).not.toBe(room!.joinCode);
    expect(updated.studentCount).toBe(before);
  });

  it('duplicar sala copia a trilha, mas não alunos nem progresso', async () => {
    const page = await rooms.list();
    const room = page.content.find((r) => r.studentCount > 0 && r.lessonCount > 0);
    expect(room).toBeDefined();

    const copy = await rooms.duplicate(room!.id);

    expect(copy.lessonCount).toBe(room!.lessonCount);
    expect(copy.studentCount).toBe(0);
    expect(copy.joinCode).not.toBe(room!.joinCode);
  });

  it('não exclui sala já usada — o caminho é arquivar', async () => {
    const page = await rooms.list();
    const used = page.content.find((r) => r.studentCount > 0);

    await expect(rooms.remove(used!.id)).rejects.toMatchObject({ status: 409 });

    const archived = await rooms.archive(used!.id);
    expect(archived.archived).toBe(true);
  });

  it('remover aluno preserva o histórico', async () => {
    const page = await rooms.list();
    const room = page.content.find((r) => r.studentCount > 0)!;
    const students = TestBed.inject(TeacherStudentService);

    const before = await students.list(room.id);
    const target = before.content.find((s) => s.membershipStatus === 'ACTIVE')!;
    const xpBefore = target.xp;

    await students.remove(room.id, target.studentId);

    const after = await students.list(room.id);
    const removed = after.content.find((s) => s.studentId === target.studentId)!;

    expect(removed.membershipStatus).toBe('REMOVED');
    // O XP continua lá: reingressar restaura tudo.
    expect(removed.xp).toBe(xpBefore);
  });

  it('recusa publicar lição sem questões e aceita depois de criar uma', async () => {
    const draft = await lessons.create({
      title: 'Lição sem questões',
      summary: null,
      theoryMarkdown: '## Teoria',
    });

    await expect(lessons.publish(draft.id)).rejects.toMatchObject({ status: 422 });

    await questions.create(draft.id, {
      prompt: 'Quanto é 10% de 100?',
      type: 'SINGLE_CHOICE',
      explanation: 'É 10.',
      options: [
        { id: null, text: '10', correct: true },
        { id: null, text: '20', correct: false },
      ],
    });

    const published = await lessons.publish(draft.id);
    expect(published.status).toBe('PUBLISHED');
  });

  it('valida as regras de cada tipo de questão', async () => {
    const draft = await lessons.create({ title: 'Validação', summary: null, theoryMarkdown: '' });

    // Escolha única exige exatamente uma correta.
    await expect(
      questions.create(draft.id, {
        prompt: 'Duas corretas?',
        type: 'SINGLE_CHOICE',
        explanation: null,
        options: [
          { id: null, text: 'A', correct: true },
          { id: null, text: 'B', correct: true },
        ],
      }),
    ).rejects.toMatchObject({ status: 422 });

    // Múltipla escolha exige duas ou mais.
    await expect(
      questions.create(draft.id, {
        prompt: 'Só uma correta?',
        type: 'MULTIPLE_CHOICE',
        explanation: null,
        options: [
          { id: null, text: 'A', correct: true },
          { id: null, text: 'B', correct: false },
        ],
      }),
    ).rejects.toMatchObject({ status: 422 });

    // Numérica guarda o valor como string, sem virar float.
    const numeric = await questions.create(draft.id, {
      prompt: 'Qual o montante?',
      type: 'NUMERIC',
      explanation: null,
      correctNumericValue: '1268.24',
      absoluteTolerance: '0.50',
      unit: 'BRL',
      decimalPlaces: 2,
    });
    expect(numeric.correctNumericValue).toBe('1268.24');
    expect(typeof numeric.correctNumericValue).toBe('string');
  });

  it('questão já respondida é arquivada, não removida', async () => {
    const page = await rooms.list();
    const room = page.content.find((r) => r.lessonCount > 0)!;
    const track = await assignments.list(room.id);
    const lessonId = track[0].lesson.id;

    const list = await questions.list(lessonId);
    const result = await questions.remove(list[0].id);

    // A semente cria tentativas nesta lição, então o esperado é arquivamento.
    expect(result.archived).toBe(true);

    const after = await questions.list(lessonId);
    expect(after.find((q) => q.id === list[0].id)?.archived).toBe(true);
  });

  it('atribuição aceita null como "sem limite" e envia instantes em UTC', async () => {
    const page = await rooms.list();
    const room = page.content[0];
    const acervo = await lessons.publishedOptions();

    const availableFrom = new Date(Date.now() + 86_400_000).toISOString();
    const dueAt = new Date(Date.now() + 7 * 86_400_000).toISOString();

    const created = await assignments.create(room.id, {
      lessonId: acervo[0].id,
      position: 99,
      status: 'DRAFT',
      availableFrom,
      dueAt,
      timeLimitMinutes: null,
      maxAttempts: null,
      questionCount: null,
      shuffleQuestions: true,
      shuffleOptions: false,
    });

    // Os três `null` sobrevivem à ida e volta — não viram zero nem padrão.
    expect(created.timeLimitMinutes).toBeNull();
    expect(created.maxAttempts).toBeNull();
    expect(created.questionCount).toBeNull();

    // Instantes voltam em UTC ISO 8601.
    expect(created.availableFrom).toBe(availableFrom);
    expect(created.dueAt).toBe(dueAt);
    expect(created.availableFrom?.endsWith('Z')).toBe(true);
  });

  it('recusa prazo anterior à abertura', async () => {
    const page = await rooms.list();
    const room = page.content[0];
    const acervo = await lessons.publishedOptions();

    await expect(
      assignments.create(room.id, {
        lessonId: acervo[0].id,
        position: 1,
        status: 'DRAFT',
        availableFrom: new Date(Date.now() + 7 * 86_400_000).toISOString(),
        dueAt: new Date(Date.now() + 86_400_000).toISOString(),
        timeLimitMinutes: 30,
        maxAttempts: 3,
        questionCount: null,
        shuffleQuestions: true,
        shuffleOptions: true,
      }),
    ).rejects.toMatchObject({ status: 422 });
  });

  it('não retira da trilha uma atribuição já usada', async () => {
    const page = await rooms.list();
    const room = page.content.find((r) => r.lessonCount > 0)!;
    const track = await assignments.list(room.id);

    const used = track.find((item) => !item.removable);
    expect(used).toBeDefined();

    await expect(assignments.remove(room.id, used!.id)).rejects.toMatchObject({ status: 409 });
  });

  it('o relatório traz métricas prontas e o CSV vem do servidor', async () => {
    const reports = TestBed.inject(ReportService);
    const filters = {
      roomId: null,
      lessonId: null,
      period: 'ALL' as const,
      from: null,
      to: null,
    };

    const overview = await reports.overview(filters);
    expect(overview.metrics.studentCount).toBeGreaterThan(0);
    expect(overview.scoreDistribution.length).toBe(4);
    expect(overview.generatedAt).toBeTruthy();

    const ranking = await reports.ranking(filters);
    // O professor vê nomes completos das próprias salas — §10 da spec.
    expect(ranking.content[0].fullName).not.toMatch(/^\S+ \S\.$/);

    const csv = await reports.exportCsv(filters);
    expect(csv).toBeInstanceOf(Blob);
  });

  it('não deixa o professor tocar em sala de outro professor', async () => {
    const page = await rooms.list();
    const roomId = page.content[0].id;

    await auth.logout().catch(() => undefined);
    await auth.login({ email: 'carla@contacerta.dev', password: 'senha123' });

    await expect(rooms.get(roomId)).rejects.toMatchObject({ status: 403 });
  });
});
