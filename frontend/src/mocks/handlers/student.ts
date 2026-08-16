import type { RoomSummary } from '../../app/core/models/room';
import { db, type DbAttempt, type DbMaterial, type DbRoom, type DbUser, type DbVideo } from '../db';
import {
  activeAttemptOf,
  assignmentsOfRoom,
  attemptsOf,
  attemptsRemaining,
  availabilityFor,
  bestAttemptOf,
  buildSnapshots,
  canStartAttempt,
  gradeAttempt,
  isAnswerCorrect,
  isPassed,
  lessonsPassedOf,
  levelOf,
  levelProgressOf,
  roomStarsOf,
  roomXpOf,
} from '../rules';
import {
  NO_CONTENT,
  conflict,
  forbidden,
  gone,
  notFound,
  problem,
  unprocessable,
  type MockContext,
  type MockRoute,
} from '../router';
import {
  anonymizeName,
  isPast,
  isoIn,
  mutate,
  newId,
  nowIso,
  paginate,
  requireRole,
  toInstitutionSummary,
} from '../support';

/**
 * Handlers do aluno — §6 da spec de integração.
 *
 * Aplicam as regras que o frontend tem proibido aplicar: disponibilidade,
 * correção, nota, estrelas e XP saem daqui prontos, como sairiam do backend.
 */

function student(context: MockContext): DbUser {
  return requireRole(context, 'STUDENT');
}

/** Sala em que o aluno tem matrícula ativa, ou erro. */
function activeRoom(roomId: string, studentId: string): DbRoom {
  const room = db().rooms.find((r) => r.id === roomId);
  if (!room) {
    throw notFound('Sala não encontrada.');
  }

  const membership = db().memberships.find(
    (m) => m.roomId === roomId && m.studentId === studentId,
  );
  if (!membership || membership.status !== 'ACTIVE') {
    throw forbidden('Você não participa desta sala.');
  }

  return room;
}

function toRoomSummary(room: DbRoom, studentId: string): RoomSummary {
  const institution = db().institutions.find((i) => i.id === room.institutionId);
  const teacher = db().users.find((u) => u.id === room.teacherId);
  const membership = db().memberships.find(
    (m) => m.roomId === room.id && m.studentId === studentId,
  );

  const assignments = assignmentsOfRoom(room.id);
  const passed = lessonsPassedOf(room.id, studentId, room);

  return {
    id: room.id,
    name: room.name,
    description: room.description,
    grade: room.grade,
    contentTopics: [...room.contentTopics],
    teacher: { id: room.teacherId, fullName: teacher?.fullName ?? 'Professor' },
    institution: institution
      ? toInstitutionSummary(institution)
      : {
          id: room.institutionId,
          name: '',
          cnpj: '',
          contactEmail: '',
          contactPhone: '',
          active: true,
        },
    membershipStatus: membership?.status ?? 'REMOVED',
    archived: room.archivedAt !== null,
    progressPercent:
      assignments.length === 0 ? 0 : Math.round((passed / assignments.length) * 100),
  };
}

function lessonTitle(lessonId: string): string {
  return db().lessons.find((l) => l.id === lessonId)?.title ?? 'Lição';
}

function rulesOf(assignmentId: string, room: DbRoom, studentId: string) {
  const assignment = db().assignments.find((a) => a.id === assignmentId);
  if (!assignment) {
    throw notFound('Atribuição não encontrada.');
  }

  const questionCount =
    assignment.questionCount ??
    db().questions.filter((q) => q.lessonId === assignment.lessonId && q.active).length;

  return {
    timeLimitMinutes: assignment.timeLimitMinutes,
    maxAttempts: assignment.maxAttempts,
    attemptsUsed: attemptsOf(assignmentId, studentId).length,
    attemptsRemaining: attemptsRemaining(assignment, studentId),
    questionCount,
    passingScorePercent: room.passingScorePercent,
  };
}

/** Item da trilha, com estado e motivo de bloqueio já decididos. */
function trackItem(roomId: string, index: number, studentId: string) {
  const assignments = assignmentsOfRoom(roomId);
  const assignment = assignments[index];
  const room = db().rooms.find((r) => r.id === roomId) as DbRoom;
  const previous = index > 0 ? assignments[index - 1] : null;

  const { availability, lockReason } = availabilityFor(assignment, room, studentId, previous);
  const best = bestAttemptOf(assignment.id, studentId);
  const active = activeAttemptOf(assignment.id, studentId);

  return {
    assignmentId: assignment.id,
    lessonId: assignment.lessonId,
    title: lessonTitle(assignment.lessonId),
    summary: db().lessons.find((l) => l.id === assignment.lessonId)?.summary ?? null,
    order: assignment.position,
    availability,
    lockReason,
    availableFrom: assignment.availableFrom,
    dueAt: assignment.dueAt,
    rules: rulesOf(assignment.id, room, studentId),
    bestScorePercent: best?.scorePercent ?? null,
    stars: best?.stars ?? null,
    activeAttemptId: active?.id ?? null,
    activeAttemptExpiresAt: active?.expiresAt ?? null,
    bestAttemptId: best?.id ?? null,
  };
}

/** Questões da tentativa **sem gabarito** — o contrato da §6.3. */
function toAttemptQuestions(attempt: DbAttempt) {
  return attempt.questions.map((snapshot) => ({
    questionSnapshotId: snapshot.id,
    type: snapshot.type,
    prompt: snapshot.prompt,
    order: snapshot.order,
    ...(snapshot.options.length > 0
      ? { options: snapshot.options.map((o) => ({ id: o.id, text: o.text })) }
      : {}),
    ...(snapshot.type === 'NUMERIC'
      ? { numeric: { unit: snapshot.unit, decimalPlaces: snapshot.decimalPlaces } }
      : {}),
  }));
}

function attemptOwnedBy(attemptId: string, studentId: string): DbAttempt {
  const attempt = db().attempts.find((a) => a.id === attemptId);
  if (!attempt) {
    throw notFound('Tentativa não encontrada.');
  }
  if (attempt.studentId !== studentId) {
    throw forbidden('Esta tentativa não é sua.');
  }
  return attempt;
}

function roomOfAttempt(attempt: DbAttempt): DbRoom {
  const assignment = db().assignments.find((a) => a.id === attempt.assignmentId);
  const room = db().rooms.find((r) => r.id === assignment?.roomId);
  if (!room) {
    throw notFound('Sala da tentativa não encontrada.');
  }
  return room;
}

/**
 * Expira a tentativa se o prazo passou.
 *
 * **O servidor expira e corrige por conta própria**, independentemente do
 * cliente (§6.3 da spec). Chamado em toda leitura, é isto que faz uma
 * tentativa abandonada aparecer corrigida quando o aluno volta.
 */
function expireIfDue(attempt: DbAttempt): void {
  if (attempt.status === 'IN_PROGRESS' && attempt.expiresAt && isPast(attempt.expiresAt)) {
    gradeAttempt(attempt, roomOfAttempt(attempt), true);
  }
}

function mediaAssignmentsOf(roomId: string, type: 'VIDEO' | 'MATERIAL') {
  return db().mediaAssignments.filter((m) => m.roomId === roomId && m.mediaType === type);
}

function viewOf(type: 'VIDEO' | 'MATERIAL', mediaId: string, studentId: string) {
  return db().mediaViews.find(
    (v) => v.mediaType === type && v.mediaId === mediaId && v.studentId === studentId,
  );
}

export const studentRoutes: MockRoute[] = [
  {
    method: 'GET',
    path: '/student/rooms',
    handler: (context) => {
      const user = student(context);
      return db()
        .memberships.filter((m) => m.studentId === user.id && m.status === 'ACTIVE')
        .map((m) => db().rooms.find((r) => r.id === m.roomId))
        .filter((room): room is DbRoom => room !== undefined)
        .map((room) => toRoomSummary(room, user.id));
    },
  },

  {
    method: 'POST',
    path: '/student/rooms/join',
    handler: (context) => {
      const user = student(context);
      const { code } = (context.body ?? {}) as { code?: string };

      const room = db().rooms.find((r) => r.joinCode === (code ?? '').toUpperCase());
      if (!room) {
        throw notFound('Código não encontrado.');
      }
      if (room.institutionId !== user.institutionId) {
        throw forbidden('Esta sala pertence a outra instituição.');
      }
      if (room.archivedAt) {
        throw gone('Esta sala está arquivada e não aceita novos alunos.');
      }

      return mutate(() => {
        const existing = db().memberships.find(
          (m) => m.roomId === room.id && m.studentId === user.id,
        );

        if (existing?.status === 'ACTIVE') {
          throw conflict('ALREADY_MEMBER', 'Você já participa desta sala.');
        }

        if (existing) {
          // Reingresso reativa a matrícula e restaura todo o histórico.
          existing.status = 'ACTIVE';
          existing.removedAt = null;
        } else {
          db().memberships.push({
            roomId: room.id,
            studentId: user.id,
            status: 'ACTIVE',
            joinedAt: nowIso(),
            removedAt: null,
          });
        }

        room.everUsed = true;
        return toRoomSummary(room, user.id);
      });
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/dashboard',
    handler: (context) => {
      const user = student(context);
      const room = activeRoom(context.params['roomId'], user.id);

      const assignments = assignmentsOfRoom(room.id);
      const xp = roomXpOf(room.id, user.id);
      const passed = lessonsPassedOf(room.id, user.id, room);

      // Próxima lição: a primeira não aprovada e não bloqueada.
      const next = assignments
        .map((assignment, index) => ({ assignment, index }))
        .find(({ assignment, index }) => {
          const previous = index > 0 ? assignments[index - 1] : null;
          const { availability } = availabilityFor(assignment, room, user.id, previous);
          return availability === 'AVAILABLE' || availability === 'IN_PROGRESS' || availability === 'FAILED';
        });

      const ranking = rankingOf(room.id);
      const myPosition = ranking.findIndex((entry) => entry.studentId === user.id);

      return {
        room: toRoomSummary(room, user.id),
        progressPercent:
          assignments.length === 0 ? 0 : Math.round((passed / assignments.length) * 100),
        level: levelOf(xp),
        xpTotal: xp,
        levelProgressPercent: levelProgressOf(xp),
        xpToNextLevel: 100 - levelProgressOf(xp),
        starsTotal: roomStarsOf(room.id, user.id),
        starsPossible: assignments.length * 3,
        lessonsCompleted: passed,
        lessonsTotal: assignments.length,
        rankingPosition: myPosition >= 0 ? myPosition + 1 : null,
        rankingParticipants: ranking.length,
        nextLesson: next
          ? {
              assignmentId: next.assignment.id,
              lessonId: next.assignment.lessonId,
              title: lessonTitle(next.assignment.lessonId),
              order: next.assignment.position,
              activeAttemptId: activeAttemptOf(next.assignment.id, user.id)?.id ?? null,
            }
          : null,
        recentAchievements: achievementsOf(room.id, user.id).filter((a) => a.unlocked).slice(0, 3),
        tipOfDay: tipOfDay(),
      };
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/lessons',
    handler: (context) => {
      const user = student(context);
      const room = activeRoom(context.params['roomId'], user.id);
      return assignmentsOfRoom(room.id).map((_, index) => trackItem(room.id, index, user.id));
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/lessons/:lessonId',
    handler: (context) => {
      const user = student(context);
      const roomId = context.params['roomId'];
      const lessonId = context.params['lessonId'];
      const room = activeRoom(roomId, user.id);

      const assignments = assignmentsOfRoom(roomId);
      const index = assignments.findIndex((a) => a.lessonId === lessonId);
      if (index === -1) {
        throw notFound('Esta lição não está nesta sala.');
      }

      const item = trackItem(roomId, index, user.id);
      const lesson = db().lessons.find((l) => l.id === lessonId);

      const materials = mediaAssignmentsOf(roomId, 'MATERIAL')
        .filter((m) => m.lessonId === lessonId)
        .map((m) => toStudentMaterial(m.mediaId, m.lessonId, user.id))
        .filter((m) => m !== null);

      return {
        assignmentId: item.assignmentId,
        lessonId,
        roomId,
        title: item.title,
        summary: item.summary,
        theoryMarkdown: lesson?.theoryMarkdown ?? '',
        materials,
        availability: item.availability,
        lockReason: item.lockReason,
        availableFrom: item.availableFrom,
        dueAt: item.dueAt,
        rules: rulesOf(item.assignmentId, room, user.id),
        bestScorePercent: item.bestScorePercent,
        stars: item.stars,
        activeAttemptId: item.activeAttemptId,
        bestAttemptId: item.bestAttemptId,
      };
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/lessons/:lessonId/attempts',
    handler: (context) => {
      const user = student(context);
      const roomId = context.params['roomId'];
      activeRoom(roomId, user.id);

      const assignment = assignmentsOfRoom(roomId).find(
        (a) => a.lessonId === context.params['lessonId'],
      );
      if (!assignment) {
        throw notFound('Esta lição não está nesta sala.');
      }

      const attempts = attemptsOf(assignment.id, user.id);
      attempts.forEach(expireIfDue);

      const best = bestAttemptOf(assignment.id, user.id);

      return [...attempts]
        .reverse()
        .map((attempt) => ({
          attemptId: attempt.id,
          status: attempt.status,
          startedAt: attempt.startedAt,
          submittedAt: attempt.submittedAt,
          scorePercent: attempt.status === 'IN_PROGRESS' ? null : attempt.scorePercent,
          stars: attempt.status === 'IN_PROGRESS' ? null : attempt.stars,
          passed: attempt.passed,
          correctAnswers: attempt.status === 'IN_PROGRESS' ? null : attempt.correctAnswers,
          totalQuestions: attempt.questions.length,
          best: best?.id === attempt.id,
        }));
    },
  },

  {
    method: 'POST',
    path: '/student/room-lessons/:assignmentId/attempts',
    handler: (context) => {
      const user = student(context);
      const assignmentId = context.params['assignmentId'];

      const assignment = db().assignments.find((a) => a.id === assignmentId);
      if (!assignment) {
        throw notFound('Atribuição não encontrada.');
      }

      const room = activeRoom(assignment.roomId, user.id);
      if (room.archivedAt) {
        throw gone('Esta sala está arquivada e não aceita novas tentativas.');
      }

      const idempotencyKey = context.request.headers.get('Idempotency-Key');

      return mutate(() => {
        // Mesma intenção, mesma chave: devolve a tentativa já criada em vez de
        // criar outra (§6.3 da spec).
        if (idempotencyKey) {
          const existing = db().attempts.find(
            (a) => a.idempotencyKey === idempotencyKey && a.studentId === user.id,
          );
          if (existing) {
            return toStartResponse(existing);
          }
        }

        const active = activeAttemptOf(assignmentId, user.id);
        if (active) {
          expireIfDue(active);
          if (active.status === 'IN_PROGRESS') {
            return toStartResponse(active);
          }
        }

        const assignments = assignmentsOfRoom(room.id);
        const index = assignments.findIndex((a) => a.id === assignmentId);
        const previous = index > 0 ? assignments[index - 1] : null;

        const { allowed, reason } = canStartAttempt(assignment, room, user.id, previous);
        if (!allowed) {
          throw problem(422, reason ?? 'NOT_ALLOWED', lockMessage(reason));
        }

        const questions = buildSnapshots(assignment);
        if (questions.length === 0) {
          throw unprocessable('Esta lição ainda não tem questões suficientes.');
        }

        const attempt: DbAttempt = {
          id: newId('attempt'),
          assignmentId,
          studentId: user.id,
          status: 'IN_PROGRESS',
          startedAt: nowIso(),
          expiresAt:
            assignment.timeLimitMinutes === null
              ? null
              : isoIn(assignment.timeLimitMinutes * 60_000),
          submittedAt: null,
          questions,
          answers: [],
          correctAnswers: 0,
          scorePercent: 0,
          passed: false,
          stars: 0,
          xpEarned: 0,
          idempotencyKey,
        };

        db().attempts.push(attempt);
        return toStartResponse(attempt);
      });
    },
  },

  {
    method: 'GET',
    path: '/student/attempts/:attemptId',
    handler: (context) => {
      const user = student(context);
      const attempt = attemptOwnedBy(context.params['attemptId'], user.id);

      return mutate(() => {
        expireIfDue(attempt);

        const assignment = db().assignments.find((a) => a.id === attempt.assignmentId);
        const room = roomOfAttempt(attempt);

        return {
          attemptId: attempt.id,
          assignmentId: attempt.assignmentId,
          roomId: room.id,
          lessonId: assignment?.lessonId ?? '',
          lessonTitle: lessonTitle(assignment?.lessonId ?? ''),
          status: attempt.status,
          startedAt: attempt.startedAt,
          expiresAt: attempt.expiresAt,
          timeLimitMinutes: assignment?.timeLimitMinutes ?? null,
          questions: toAttemptQuestions(attempt),
          // Respostas já registradas, **sem** o campo de correção.
          answers: attempt.answers.map((answer) => ({
            questionSnapshotId: answer.questionSnapshotId,
            answeredAt: answer.answeredAt,
            answer: {
              ...(answer.selectedOptionIds ? { selectedOptionIds: answer.selectedOptionIds } : {}),
              ...(answer.booleanValue !== null ? { booleanValue: answer.booleanValue } : {}),
              ...(answer.numericValue !== null ? { numericValue: answer.numericValue } : {}),
            },
          })),
          passingScorePercent: room.passingScorePercent,
        };
      });
    },
  },

  {
    method: 'PUT',
    path: '/student/attempts/:attemptId/answers/:questionSnapshotId',
    handler: (context) => {
      const user = student(context);
      const attempt = attemptOwnedBy(context.params['attemptId'], user.id);
      const snapshotId = context.params['questionSnapshotId'];

      return mutate(() => {
        expireIfDue(attempt);

        if (attempt.status !== 'IN_PROGRESS') {
          throw gone('Esta tentativa já foi encerrada.');
        }

        const snapshot = attempt.questions.find((q) => q.id === snapshotId);
        if (!snapshot) {
          throw notFound('Questão não pertence a esta tentativa.');
        }

        // A resposta é imutável dentro da tentativa (§6.3 da spec).
        if (attempt.answers.some((a) => a.questionSnapshotId === snapshotId)) {
          throw conflict('ANSWER_ALREADY_RECORDED', 'Esta questão já foi respondida.');
        }

        const payload = (context.body ?? {}) as {
          selectedOptionIds?: string[];
          booleanValue?: boolean;
          numericValue?: string;
        };

        const correct = isAnswerCorrect(snapshot, payload);

        attempt.answers.push({
          questionSnapshotId: snapshotId,
          answeredAt: nowIso(),
          selectedOptionIds: payload.selectedOptionIds ?? null,
          booleanValue: payload.booleanValue ?? null,
          numericValue: payload.numericValue ?? null,
          correct,
        });

        // A API devolve `correct` — é o cliente que descarta esse campo na
        // borda HTTP, e há teste garantindo que ele não chega à tela.
        return { questionSnapshotId: snapshotId, answeredAt: nowIso(), correct };
      });
    },
  },

  {
    method: 'POST',
    path: '/student/attempts/:attemptId/submit',
    handler: (context) => {
      const user = student(context);
      const attempt = attemptOwnedBy(context.params['attemptId'], user.id);

      return mutate(() => {
        expireIfDue(attempt);

        if (attempt.status !== 'IN_PROGRESS') {
          throw gone('Esta tentativa já foi encerrada.');
        }

        gradeAttempt(attempt, roomOfAttempt(attempt), false);
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/student/attempts/:attemptId/result',
    handler: (context) => {
      const user = student(context);
      const attempt = attemptOwnedBy(context.params['attemptId'], user.id);

      return mutate(() => {
        expireIfDue(attempt);

        if (attempt.status === 'IN_PROGRESS') {
          throw conflict('ATTEMPT_IN_PROGRESS', 'Esta tentativa ainda não foi finalizada.');
        }

        const assignment = db().assignments.find((a) => a.id === attempt.assignmentId);
        const room = roomOfAttempt(attempt);

        return {
          attemptId: attempt.id,
          assignmentId: attempt.assignmentId,
          roomId: room.id,
          lessonId: assignment?.lessonId ?? '',
          lessonTitle: lessonTitle(assignment?.lessonId ?? ''),
          status: attempt.status,
          correctAnswers: attempt.correctAnswers,
          totalQuestions: attempt.questions.length,
          scorePercent: attempt.scorePercent,
          passed: attempt.passed,
          stars: attempt.stars,
          xpEarnedThisAttempt: attempt.xpEarned,
          roomXpTotal: roomXpOf(room.id, user.id),
          startedAt: attempt.startedAt,
          submittedAt: attempt.submittedAt,
          passingScorePercent: room.passingScorePercent,
          attemptsRemaining: assignment ? attemptsRemaining(assignment, user.id) : null,
          answers: attempt.questions.map((snapshot) => {
            const given = attempt.answers.find((a) => a.questionSnapshotId === snapshot.id);
            return {
              question: {
                questionSnapshotId: snapshot.id,
                type: snapshot.type,
                prompt: snapshot.prompt,
                order: snapshot.order,
                ...(snapshot.options.length > 0
                  ? { options: snapshot.options.map((o) => ({ id: o.id, text: o.text })) }
                  : {}),
                ...(snapshot.type === 'NUMERIC'
                  ? { numeric: { unit: snapshot.unit, decimalPlaces: snapshot.decimalPlaces } }
                  : {}),
              },
              studentAnswer: given
                ? {
                    ...(given.selectedOptionIds ? { selectedOptionIds: given.selectedOptionIds } : {}),
                    ...(given.booleanValue !== null ? { booleanValue: given.booleanValue } : {}),
                    ...(given.numericValue !== null ? { numericValue: given.numericValue } : {}),
                  }
                : null,
              correctAnswer: correctAnswerOf(snapshot),
              correct: given?.correct ?? false,
              explanation: snapshot.explanation,
            };
          }),
        };
      });
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/videos',
    handler: (context) => {
      const user = student(context);
      const roomId = context.params['roomId'];
      activeRoom(roomId, user.id);

      const items = mediaAssignmentsOf(roomId, 'VIDEO')
        .map((assignment) => {
          const video = db().videos.find(
            (v) => v.id === assignment.mediaId && v.status === 'PUBLISHED',
          );
          return video ? toStudentVideo(video, assignment.lessonId, user.id) : null;
        })
        .filter((item): item is NonNullable<typeof item> => item !== null);

      return {
        items,
        viewedCount: items.filter((item) => item.viewed).length,
        totalCount: items.length,
      };
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/materials',
    handler: (context) => {
      const user = student(context);
      const roomId = context.params['roomId'];
      activeRoom(roomId, user.id);

      const items = mediaAssignmentsOf(roomId, 'MATERIAL')
        .map((assignment) => toStudentMaterial(assignment.mediaId, assignment.lessonId, user.id))
        .filter((item): item is NonNullable<typeof item> => item !== null);

      return {
        items,
        viewedCount: items.filter((item) => item.viewed).length,
        totalCount: items.length,
      };
    },
  },

  {
    method: 'POST',
    path: '/student/media/:mediaType/:mediaId/view',
    handler: (context) => {
      const user = student(context);
      const mediaType = context.params['mediaType'] as 'VIDEO' | 'MATERIAL';
      const mediaId = context.params['mediaId'];

      return mutate(() => {
        // Idempotente por aluno e mídia: preserva `firstViewedAt` e atualiza
        // `lastViewedAt` (§7.4 da spec).
        const existing = viewOf(mediaType, mediaId, user.id);
        if (existing) {
          existing.lastViewedAt = nowIso();
        } else {
          db().mediaViews.push({
            mediaType,
            mediaId,
            studentId: user.id,
            firstViewedAt: nowIso(),
            lastViewedAt: nowIso(),
          });
        }
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/ranking',
    handler: (context) => {
      const user = student(context);
      const roomId = context.params['roomId'];
      activeRoom(roomId, user.id);

      const entries = rankingOf(roomId).map((entry, index) => ({
        position: index + 1,
        studentId: entry.studentId,
        // Anonimização é do servidor: o cliente recebe já pronto.
        displayName:
          entry.studentId === user.id ? entry.fullName : anonymizeName(entry.fullName),
        xp: entry.xp,
        stars: entry.stars,
        lessonsPassed: entry.lessonsPassed,
        me: entry.studentId === user.id,
      }));

      const page = paginate(entries, context);
      return { ...page, me: entries.find((entry) => entry.me) ?? null };
    },
  },

  {
    method: 'GET',
    path: '/student/rooms/:roomId/achievements',
    handler: (context) => {
      const user = student(context);
      const roomId = context.params['roomId'];
      activeRoom(roomId, user.id);
      return achievementsOf(roomId, user.id);
    },
  },

  {
    method: 'GET',
    path: '/files/:fileId/download',
    handler: (context) => {
      requireRole(context, 'STUDENT', 'TEACHER', 'ADMIN');
      const file = db().files.find((f) => f.id === context.params['fileId']);
      if (!file) {
        throw notFound('Arquivo não encontrado.');
      }
      return new Blob([file.content], { type: file.mimeType });
    },
  },
];

function toStartResponse(attempt: DbAttempt) {
  return {
    attemptId: attempt.id,
    status: attempt.status,
    startedAt: attempt.startedAt,
    expiresAt: attempt.expiresAt,
  };
}

function correctAnswerOf(snapshot: DbAttempt['questions'][number]) {
  switch (snapshot.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return { selectedOptionIds: snapshot.options.filter((o) => o.correct).map((o) => o.id) };
    case 'TRUE_FALSE':
      return { booleanValue: snapshot.correctBoolean ?? false };
    case 'NUMERIC':
      return { numericValue: snapshot.correctNumericValue ?? '' };
  }
}

function toStudentVideo(video: DbVideo, lessonId: string | null, studentId: string) {
  const view = viewOf('VIDEO', video.id, studentId);
  return {
    id: video.id,
    title: video.title,
    description: video.description,
    url: video.url,
    durationMinutes: null,
    lesson: lessonId ? { lessonId, lessonTitle: lessonTitle(lessonId) } : null,
    viewed: view !== undefined,
    firstViewedAt: view?.firstViewedAt ?? null,
  };
}

function toStudentMaterial(materialId: string, lessonId: string | null, studentId: string) {
  const material: DbMaterial | undefined = db().materials.find(
    (m) => m.id === materialId && m.status === 'PUBLISHED',
  );
  if (!material) {
    return null;
  }

  const file = material.fileId ? db().files.find((f) => f.id === material.fileId) : undefined;
  const view = viewOf('MATERIAL', material.id, studentId);

  return {
    id: material.id,
    title: material.title,
    description: material.description,
    kind: material.kind,
    externalUrl: material.url,
    fileId: material.fileId,
    fileName: file?.name ?? null,
    fileSizeBytes: file?.size ?? null,
    contentType: file?.mimeType ?? null,
    lesson: lessonId ? { lessonId, lessonTitle: lessonTitle(lessonId) } : null,
    viewed: view !== undefined,
    firstViewedAt: view?.firstViewedAt ?? null,
  };
}

interface RankingRow {
  studentId: string;
  fullName: string;
  xp: number;
  stars: number;
  lessonsPassed: number;
}

/** Ranking ordenado por XP, com desempate por estrelas e depois por nome. */
function rankingOf(roomId: string): RankingRow[] {
  const room = db().rooms.find((r) => r.id === roomId);
  if (!room) {
    return [];
  }

  return db()
    .memberships.filter((m) => m.roomId === roomId && m.status === 'ACTIVE')
    .map((membership) => {
      const user = db().users.find((u) => u.id === membership.studentId);
      return {
        studentId: membership.studentId,
        fullName: user?.fullName ?? 'Aluno',
        xp: roomXpOf(roomId, membership.studentId),
        stars: roomStarsOf(roomId, membership.studentId),
        lessonsPassed: lessonsPassedOf(roomId, membership.studentId, room),
      };
    })
    .sort(
      (a, b) =>
        b.xp - a.xp || b.stars - a.stars || a.fullName.localeCompare(b.fullName, 'pt-BR'),
    );
}

/** As sete conquistas fixas da primeira versão — §6.4 da spec. */
function achievementsOf(roomId: string, studentId: string) {
  const room = db().rooms.find((r) => r.id === roomId);
  if (!room) {
    return [];
  }

  const xp = roomXpOf(roomId, studentId);
  const passed = lessonsPassedOf(roomId, studentId, room);

  const attempts = assignmentsOfRoom(roomId)
    .flatMap((assignment) => attemptsOf(assignment.id, studentId))
    .filter((attempt) => attempt.status !== 'IN_PROGRESS');

  const firstPass = attempts.find((a) => isPassed(a, room)) ?? null;
  const firstPerfect = attempts.find((a) => a.scorePercent === 100) ?? null;

  const build = (
    code: string,
    title: string,
    description: string,
    icon: string,
    unlocked: boolean,
    unlockedAt: string | null,
    progressCurrent: number | null = null,
    progressTarget: number | null = null,
  ) => ({
    code,
    title,
    description,
    icon,
    unlocked,
    unlockedAt,
    progressCurrent: unlocked ? null : progressCurrent,
    progressTarget: unlocked ? null : progressTarget,
  });

  return [
    build(
      'FIRST_PASS',
      'Primeira aprovação',
      'Seja aprovado em uma lição.',
      'pi pi-check-circle',
      firstPass !== null,
      firstPass?.submittedAt ?? null,
    ),
    build(
      'FIRST_PERFECT_SCORE',
      'Nota perfeita',
      'Acerte todas as questões de uma lição.',
      'pi pi-verified',
      firstPerfect !== null,
      firstPerfect?.submittedAt ?? null,
    ),
    build('XP_100', '100 XP', 'Acumule 100 XP nesta sala.', 'pi pi-bolt', xp >= 100, null, xp, 100),
    build('XP_500', '500 XP', 'Acumule 500 XP nesta sala.', 'pi pi-bolt', xp >= 500, null, xp, 500),
    build('XP_1000', '1.000 XP', 'Acumule 1.000 XP nesta sala.', 'pi pi-bolt', xp >= 1000, null, xp, 1000),
    build(
      'FIVE_LESSONS_PASSED',
      'Cinco lições',
      'Seja aprovado em cinco lições.',
      'pi pi-book',
      passed >= 5,
      null,
      passed,
      5,
    ),
    build(
      'TEN_LESSONS_PASSED',
      'Dez lições',
      'Seja aprovado em dez lições.',
      'pi pi-trophy',
      passed >= 10,
      null,
      passed,
      10,
    ),
  ];
}

/** Dica agendada para hoje; sem agendamento, uma dica ativa qualquer. */
function tipOfDay() {
  const today = new Date();
  const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

  const active = db().financialTips.filter((tip) => tip.active);
  const scheduled = active.find((tip) => tip.publicationDate === iso);
  const chosen = scheduled ?? active[0];

  return chosen
    ? {
        id: chosen.id,
        title: chosen.title,
        content: chosen.content,
        sourceUrl: chosen.sourceUrl,
        publicationDate: chosen.publicationDate,
      }
    : null;
}

function lockMessage(reason: string | null): string {
  switch (reason) {
    case 'PREREQUISITE_NOT_PASSED':
      return 'Você precisa ser aprovado na lição anterior.';
    case 'NOT_YET_AVAILABLE':
      return 'Esta lição ainda não está disponível.';
    case 'DUE_DATE_PASSED':
      return 'O prazo desta lição terminou.';
    case 'NO_ATTEMPTS_LEFT':
      return 'Você já usou todas as suas tentativas nesta lição.';
    case 'NOT_PUBLISHED':
      return 'Este conteúdo ainda não foi publicado.';
    default:
      return 'Não é possível iniciar esta tentativa agora.';
  }
}
