import { db, type DbAssignment, type DbLesson, type DbQuestion, type DbRoom, type DbUser } from '../db';
import {
  assignmentsOfRoom,
  attemptsOf,
  bestAttemptOf,
  isPassed,
  lessonsPassedOf,
  roomStarsOf,
  roomXpOf,
} from '../rules';
import {
  NO_CONTENT,
  conflict,
  forbidden,
  notFound,
  unprocessable,
  versionConflict,
  type MockContext,
  type MockRoute,
} from '../router';
import {
  generateJoinCode,
  isPast,
  matchesSearch,
  mutate,
  newId,
  nowIso,
  paginate,
  requireRole,
  toInstitutionSummary,
} from '../support';

/**
 * Handlers do professor — §7 da spec de integração.
 *
 * O ponto delicado aqui é o `version`: toda alteração confere a versão enviada
 * e responde `409 VERSION_CONFLICT` quando ela não bate. Sem isso, o
 * comportamento de conflito da interface nunca seria exercitado.
 */

function teacher(context: MockContext): DbUser {
  return requireRole(context, 'TEACHER');
}

function body<T>(context: MockContext): T {
  return (context.body ?? {}) as T;
}

/** Sala do próprio professor, ou erro. */
function ownRoom(roomId: string, teacherId: string): DbRoom {
  const room = db().rooms.find((r) => r.id === roomId);
  if (!room) {
    throw notFound('Sala não encontrada.');
  }
  if (room.teacherId !== teacherId) {
    throw forbidden('Esta sala não é sua.');
  }
  return room;
}

function ownLesson(lessonId: string, teacherId: string): DbLesson {
  const lesson = db().lessons.find((l) => l.id === lessonId);
  if (!lesson) {
    throw notFound('Lição não encontrada.');
  }
  if (lesson.teacherId !== teacherId) {
    throw forbidden('Esta lição não é sua.');
  }
  return lesson;
}

/** Confere o `version` enviado — o coração da detecção de conflito. */
function checkVersion(current: number, sent: unknown): void {
  if (typeof sent !== 'number' || sent !== current) {
    throw versionConflict();
  }
}

function activeStudentsOf(roomId: string): string[] {
  return db()
    .memberships.filter((m) => m.roomId === roomId && m.status === 'ACTIVE')
    .map((m) => m.studentId);
}

function questionsOf(lessonId: string, includeArchived = false): DbQuestion[] {
  return db()
    .questions.filter((q) => q.lessonId === lessonId && (includeArchived || q.active))
    .sort((a, b) => a.order - b.order);
}

function toRoomSummary(room: DbRoom) {
  return {
    id: room.id,
    name: room.name,
    description: room.description,
    grade: room.grade,
    contentTopics: [...room.contentTopics],
    joinCode: room.joinCode,
    passingScorePercent: room.passingScorePercent,
    archived: room.archivedAt !== null,
    studentCount: activeStudentsOf(room.id).length,
    lessonCount: assignmentsOfRoom(room.id).length,
    createdAt: room.createdAt,
    updatedAt: room.createdAt,
    version: room.version,
  };
}

function toRoomDetail(room: DbRoom) {
  const institution = db().institutions.find((i) => i.id === room.institutionId);
  const owner = db().users.find((u) => u.id === room.teacherId);

  return {
    ...toRoomSummary(room),
    institution: institution
      ? toInstitutionSummary(institution)
      : { id: room.institutionId, name: '', cnpj: '', contactEmail: '', contactPhone: '', active: true },
    teacher: { id: room.teacherId, fullName: owner?.fullName ?? 'Professor' },
    // Só sala nunca usada pode ser excluída.
    deletable: !room.everUsed && activeStudentsOf(room.id).length === 0,
  };
}

function toLessonSummary(lesson: DbLesson) {
  return {
    id: lesson.id,
    title: lesson.title,
    summary: lesson.summary || null,
    status: lesson.status,
    questionCount: questionsOf(lesson.id).length,
    assignmentCount: db().assignments.filter((a) => a.lessonId === lesson.id).length,
    createdAt: lesson.createdAt,
    updatedAt: lesson.createdAt,
    version: lesson.version,
  };
}

function toQuestion(question: DbQuestion) {
  return {
    id: question.id,
    lessonId: question.lessonId,
    prompt: question.prompt,
    type: question.type,
    explanation: question.explanation || null,
    order: question.order,
    archived: !question.active,
    version: question.version,
    options: question.options.map((o) => ({ id: o.id, text: o.text, correct: o.correct })),
    correctBoolean: question.correctBoolean,
    correctNumericValue: question.correctNumericValue,
    absoluteTolerance: question.absoluteTolerance,
    unit: question.unit,
    decimalPlaces: question.decimalPlaces,
  };
}

function toAssignment(assignment: DbAssignment) {
  const lesson = db().lessons.find((l) => l.id === assignment.lessonId);
  const hasAttempts = db().attempts.some((a) => a.assignmentId === assignment.id);

  return {
    id: assignment.id,
    roomId: assignment.roomId,
    lesson: {
      id: assignment.lessonId,
      title: lesson?.title ?? 'Lição',
      activeQuestionCount: questionsOf(assignment.lessonId).length,
    },
    position: assignment.position,
    status: assignment.status,
    availableFrom: assignment.availableFrom,
    dueAt: assignment.dueAt,
    timeLimitMinutes: assignment.timeLimitMinutes,
    maxAttempts: assignment.maxAttempts,
    questionCount: assignment.questionCount,
    shuffleQuestions: assignment.shuffleQuestions,
    shuffleOptions: assignment.shuffleOptions,
    version: assignment.version,
    // Trilha já usada não se desmonta.
    removable: !hasAttempts,
  };
}

function renumber(assignments: DbAssignment[]): void {
  assignments.forEach((assignment, index) => {
    assignment.position = index + 1;
  });
}

export const teacherRoutes: MockRoute[] = [
  {
    method: 'GET',
    path: '/teacher/dashboard',
    handler: (context) => {
      const user = teacher(context);
      const rooms = db().rooms.filter((r) => r.teacherId === user.id);
      const lessons = db().lessons.filter((l) => l.teacherId === user.id);

      const sevenDaysAgo = Date.now() - 7 * 86_400_000;
      const roomIds = new Set(rooms.map((r) => r.id));
      const assignmentIds = new Set(
        db().assignments.filter((a) => roomIds.has(a.roomId)).map((a) => a.id),
      );

      return {
        roomCount: rooms.length,
        activeRoomCount: rooms.filter((r) => r.archivedAt === null).length,
        archivedRoomCount: rooms.filter((r) => r.archivedAt !== null).length,
        studentCount: new Set(rooms.flatMap((r) => activeStudentsOf(r.id))).size,
        lessonCount: lessons.length,
        publishedLessonCount: lessons.filter((l) => l.status === 'PUBLISHED').length,
        draftLessonCount: lessons.filter((l) => l.status === 'DRAFT').length,
        recentAttemptCount: db().attempts.filter(
          (a) =>
            assignmentIds.has(a.assignmentId) &&
            a.submittedAt !== null &&
            Date.parse(a.submittedAt) >= sevenDaysAgo,
        ).length,
        recentRooms: rooms.slice(0, 5).map((room) => ({
          id: room.id,
          name: room.name,
          grade: room.grade,
          studentCount: activeStudentsOf(room.id).length,
          archived: room.archivedAt !== null,
          lastActivityAt:
            db()
              .attempts.filter((a) =>
                assignmentsOfRoom(room.id).some((assignment) => assignment.id === a.assignmentId),
              )
              .map((a) => a.submittedAt ?? a.startedAt)
              .sort()
              .pop() ?? null,
        })),
      };
    },
  },

  {
    method: 'GET',
    path: '/teacher/rooms',
    handler: (context) => {
      const user = teacher(context);
      const search = context.query.get('search');
      const archived = context.query.get('archived');

      const rooms = db()
        .rooms.filter((room) => room.teacherId === user.id)
        .filter((room) => matchesSearch(search, room.name, room.description))
        .filter((room) =>
          archived === null ? true : (room.archivedAt !== null) === (archived === 'true'),
        )
        .map(toRoomSummary);

      return paginate(rooms, context);
    },
  },

  {
    method: 'POST',
    path: '/teacher/rooms',
    handler: (context) => {
      const user = teacher(context);
      const payload = body<{
        name: string;
        description: string | null;
        grade: DbRoom['grade'];
        contentTopics: string[];
        passingScorePercent: number;
      }>(context);

      if (!payload.name || payload.name.trim().length < 3) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'name', message: 'Informe um nome com ao menos 3 caracteres.' },
        ]);
      }

      return mutate(() => {
        const room: DbRoom = {
          id: newId('room'),
          teacherId: user.id,
          // A instituição vem do professor autenticado, nunca do formulário.
          institutionId: user.institutionId ?? '',
          name: payload.name.trim(),
          description: payload.description ?? null,
          grade: payload.grade,
          contentTopics: payload.contentTopics ?? [],
          passingScorePercent: payload.passingScorePercent ?? 50,
          joinCode: generateJoinCode(),
          archivedAt: null,
          everUsed: false,
          version: 1,
          createdAt: nowIso(),
        };
        db().rooms.push(room);
        return toRoomDetail(room);
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/rooms/:roomId',
    handler: (context) =>
      toRoomDetail(ownRoom(context.params['roomId'], teacher(context).id)),
  },

  {
    method: 'PATCH',
    path: '/teacher/rooms/:roomId',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      const payload = body<Record<string, unknown>>(context);

      checkVersion(room.version, payload['version']);

      if (room.archivedAt) {
        throw conflict('ROOM_ARCHIVED', 'Sala arquivada é somente leitura.');
      }

      return mutate(() => {
        if (typeof payload['name'] === 'string') {
          room.name = payload['name'];
        }
        if ('description' in payload) {
          room.description = (payload['description'] as string | null) ?? null;
        }
        if (typeof payload['grade'] === 'string') {
          room.grade = payload['grade'] as DbRoom['grade'];
        }
        if (Array.isArray(payload['contentTopics'])) {
          room.contentTopics = payload['contentTopics'] as string[];
        }
        if (typeof payload['passingScorePercent'] === 'number') {
          room.passingScorePercent = payload['passingScorePercent'];
        }
        room.version++;
        return toRoomDetail(room);
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/rooms/:roomId/archive',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      return mutate(() => {
        room.archivedAt = nowIso();
        room.version++;
        return toRoomDetail(room);
      });
    },
  },

  {
    method: 'DELETE',
    path: '/teacher/rooms/:roomId',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);

      if (room.everUsed || activeStudentsOf(room.id).length > 0) {
        throw conflict(
          'ROOM_IN_USE',
          'Esta sala já foi usada e não pode ser excluída. Arquive-a.',
        );
      }

      return mutate(() => {
        db().rooms = db().rooms.filter((r) => r.id !== room.id);
        db().assignments = db().assignments.filter((a) => a.roomId !== room.id);
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/rooms/:roomId/duplicate',
    handler: (context) => {
      const user = teacher(context);
      const room = ownRoom(context.params['roomId'], user.id);

      return mutate(() => {
        const copy: DbRoom = {
          ...room,
          id: newId('room'),
          name: `${room.name} (cópia)`,
          joinCode: generateJoinCode(),
          archivedAt: null,
          everUsed: false,
          version: 1,
          createdAt: nowIso(),
          contentTopics: [...room.contentTopics],
        };
        db().rooms.push(copy);

        // A trilha é copiada; alunos e progresso não.
        for (const assignment of assignmentsOfRoom(room.id)) {
          db().assignments.push({
            ...assignment,
            id: newId('assign'),
            roomId: copy.id,
            version: 1,
          });
        }

        return toRoomDetail(copy);
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/rooms/:roomId/regenerate-code',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      return mutate(() => {
        room.joinCode = generateJoinCode();
        room.version++;
        return toRoomDetail(room);
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/rooms/:roomId/students',
    handler: (context) => {
      const user = teacher(context);
      const room = ownRoom(context.params['roomId'], user.id);
      const search = context.query.get('search');
      const totalLessons = assignmentsOfRoom(room.id).length;

      const rows = db()
        .memberships.filter((m) => m.roomId === room.id)
        .map((membership) => {
          const student = db().users.find((u) => u.id === membership.studentId);
          const attempts = assignmentsOfRoom(room.id).flatMap((assignment) =>
            attemptsOf(assignment.id, membership.studentId),
          );

          return {
            studentId: membership.studentId,
            fullName: student?.fullName ?? 'Aluno',
            registrationNumber: student?.registrationNumber ?? null,
            email: student?.email ?? '',
            xp: roomXpOf(room.id, membership.studentId),
            completedLessons: lessonsPassedOf(room.id, membership.studentId, room),
            totalLessons,
            stars: roomStarsOf(room.id, membership.studentId),
            lastActivityAt:
              attempts
                .map((a) => a.submittedAt ?? a.startedAt)
                .sort()
                .pop() ?? null,
            membershipStatus: membership.status,
          };
        })
        .filter((row) => matchesSearch(search, row.fullName, row.email, row.registrationNumber));

      return paginate(rows, context);
    },
  },

  {
    method: 'DELETE',
    path: '/teacher/rooms/:roomId/students/:studentId',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      const membership = db().memberships.find(
        (m) => m.roomId === room.id && m.studentId === context.params['studentId'],
      );

      if (!membership) {
        throw notFound('Este aluno não está nesta sala.');
      }

      return mutate(() => {
        // Remoção preserva o histórico: só muda a situação da matrícula.
        membership.status = 'REMOVED';
        membership.removedAt = nowIso();
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/room-lessons/:assignmentId/students/:studentId/extra-attempts',
    handler: (context) => {
      const user = teacher(context);
      const assignmentId = context.params['assignmentId'];
      const studentId = context.params['studentId'];

      const assignment = db().assignments.find((a) => a.id === assignmentId);
      if (!assignment) {
        throw notFound('Atribuição não encontrada.');
      }
      ownRoom(assignment.roomId, user.id);

      const { quantity } = body<{ quantity: number }>(context);

      return mutate(() => {
        const existing = db().extraAttempts.find(
          (e) => e.assignmentId === assignmentId && e.studentId === studentId,
        );
        const granted = quantity ?? 1;

        if (existing) {
          existing.quantity += granted;
        } else {
          db().extraAttempts.push({ assignmentId, studentId, quantity: granted });
        }

        const used = attemptsOf(assignmentId, studentId).length;
        const total =
          db().extraAttempts.find(
            (e) => e.assignmentId === assignmentId && e.studentId === studentId,
          )?.quantity ?? 0;

        return {
          assignmentId,
          studentId,
          extraAttemptsGranted: granted,
          attemptsUsed: used,
          attemptsAvailable:
            assignment.maxAttempts === null
              ? null
              : Math.max(0, assignment.maxAttempts + total - used),
        };
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/lessons',
    handler: (context) => {
      const user = teacher(context);
      const status = context.query.get('status');
      const search = context.query.get('search');

      const lessons = db()
        .lessons.filter((lesson) => lesson.teacherId === user.id)
        .filter((lesson) => (status ? lesson.status === status : true))
        .filter((lesson) => matchesSearch(search, lesson.title, lesson.summary))
        .map(toLessonSummary);

      return paginate(lessons, context);
    },
  },

  {
    method: 'POST',
    path: '/teacher/lessons',
    handler: (context) => {
      const user = teacher(context);
      const payload = body<{ title: string; summary: string | null; theoryMarkdown: string }>(
        context,
      );

      return mutate(() => {
        const lesson: DbLesson = {
          id: newId('lesson'),
          teacherId: user.id,
          title: payload.title,
          summary: payload.summary ?? '',
          theoryMarkdown: payload.theoryMarkdown ?? '',
          status: 'DRAFT',
          version: 1,
          createdAt: nowIso(),
        };
        db().lessons.push(lesson);
        return { ...toLessonSummary(lesson), theoryMarkdown: lesson.theoryMarkdown };
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/lessons/:lessonId',
    handler: (context) => {
      const lesson = ownLesson(context.params['lessonId'], teacher(context).id);
      return { ...toLessonSummary(lesson), theoryMarkdown: lesson.theoryMarkdown };
    },
  },

  {
    method: 'PATCH',
    path: '/teacher/lessons/:lessonId',
    handler: (context) => {
      const lesson = ownLesson(context.params['lessonId'], teacher(context).id);
      const payload = body<Record<string, unknown>>(context);

      checkVersion(lesson.version, payload['version']);

      return mutate(() => {
        if (typeof payload['title'] === 'string') {
          lesson.title = payload['title'];
        }
        if ('summary' in payload) {
          lesson.summary = (payload['summary'] as string | null) ?? '';
        }
        if (typeof payload['theoryMarkdown'] === 'string') {
          lesson.theoryMarkdown = payload['theoryMarkdown'];
        }
        lesson.version++;
        return { ...toLessonSummary(lesson), theoryMarkdown: lesson.theoryMarkdown };
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/lessons/:lessonId/publish',
    handler: (context) => {
      const lesson = ownLesson(context.params['lessonId'], teacher(context).id);

      // Publicação exige questões — §7.2 da spec.
      if (questionsOf(lesson.id).length === 0) {
        throw unprocessable(
          'Esta lição não tem questões ativas. Adicione ao menos uma antes de publicar.',
        );
      }

      return mutate(() => {
        lesson.status = 'PUBLISHED';
        lesson.version++;
        return { ...toLessonSummary(lesson), theoryMarkdown: lesson.theoryMarkdown };
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/lessons/:lessonId/archive',
    handler: (context) => {
      const lesson = ownLesson(context.params['lessonId'], teacher(context).id);
      return mutate(() => {
        lesson.status = 'ARCHIVED';
        lesson.version++;
        return { ...toLessonSummary(lesson), theoryMarkdown: lesson.theoryMarkdown };
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/lessons/:lessonId/duplicate',
    handler: (context) => {
      const user = teacher(context);
      const lesson = ownLesson(context.params['lessonId'], user.id);

      return mutate(() => {
        const copy: DbLesson = {
          ...lesson,
          id: newId('lesson'),
          title: `${lesson.title} (cópia)`,
          status: 'DRAFT',
          version: 1,
          createdAt: nowIso(),
        };
        db().lessons.push(copy);

        // Duplicar lição duplica as questões — §7.2 da spec.
        for (const question of questionsOf(lesson.id, true)) {
          db().questions.push({
            ...question,
            id: newId('q'),
            lessonId: copy.id,
            version: 1,
            options: question.options.map((option) => ({ ...option, id: newId('opt') })),
          });
        }

        return { ...toLessonSummary(copy), theoryMarkdown: copy.theoryMarkdown };
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/lessons/:lessonId/questions',
    handler: (context) => {
      const lesson = ownLesson(context.params['lessonId'], teacher(context).id);
      return questionsOf(lesson.id, true).map(toQuestion);
    },
  },

  {
    method: 'POST',
    path: '/teacher/lessons/:lessonId/questions',
    handler: (context) => {
      const lesson = ownLesson(context.params['lessonId'], teacher(context).id);
      const payload = body<Record<string, unknown>>(context);

      return mutate(() => {
        const question = buildQuestion(lesson.id, payload, questionsOf(lesson.id, true).length + 1);
        db().questions.push(question);
        return toQuestion(question);
      });
    },
  },

  {
    method: 'PATCH',
    path: '/teacher/questions/:questionId',
    handler: (context) => {
      const user = teacher(context);
      const question = db().questions.find((q) => q.id === context.params['questionId']);
      if (!question) {
        throw notFound('Questão não encontrada.');
      }
      ownLesson(question.lessonId, user.id);

      const payload = body<Record<string, unknown>>(context);
      checkVersion(question.version, payload['version']);

      return mutate(() => {
        applyQuestionPayload(question, payload);
        question.version++;
        return toQuestion(question);
      });
    },
  },

  {
    method: 'POST',
    path: '/teacher/questions/:questionId/duplicate',
    handler: (context) => {
      const user = teacher(context);
      const question = db().questions.find((q) => q.id === context.params['questionId']);
      if (!question) {
        throw notFound('Questão não encontrada.');
      }
      ownLesson(question.lessonId, user.id);

      const { targetLessonId } = body<{ targetLessonId: string }>(context);
      const target = ownLesson(targetLessonId, user.id);

      return mutate(() => {
        const copy: DbQuestion = {
          ...question,
          id: newId('q'),
          lessonId: target.id,
          order: questionsOf(target.id, true).length + 1,
          version: 1,
          options: question.options.map((option) => ({ ...option, id: newId('opt') })),
        };
        db().questions.push(copy);
        return toQuestion(copy);
      });
    },
  },

  {
    method: 'DELETE',
    path: '/teacher/questions/:questionId',
    handler: (context) => {
      const user = teacher(context);
      const question = db().questions.find((q) => q.id === context.params['questionId']);
      if (!question) {
        throw notFound('Questão não encontrada.');
      }
      ownLesson(question.lessonId, user.id);

      return mutate(() => {
        // Questão já respondida é arquivada, não removida: as tentativas
        // antigas precisam continuar íntegras.
        const used = db().attempts.some((attempt) =>
          attempt.questions.some((snapshot) => snapshot.questionId === question.id),
        );

        if (used) {
          question.active = false;
          question.version++;
          return { archived: true };
        }

        db().questions = db().questions.filter((q) => q.id !== question.id);
        return { archived: false };
      });
    },
  },

  {
    method: 'PUT',
    path: '/teacher/lessons/:lessonId/questions/order',
    handler: (context) => {
      const lesson = ownLesson(context.params['lessonId'], teacher(context).id);
      const { questionIds } = body<{ questionIds: string[] }>(context);

      return mutate(() => {
        questionIds.forEach((id, index) => {
          const question = db().questions.find((q) => q.id === id && q.lessonId === lesson.id);
          if (question) {
            question.order = index + 1;
          }
        });
        return questionsOf(lesson.id, true).map(toQuestion);
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/rooms/:roomId/lesson-assignments',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      return db()
        .assignments.filter((a) => a.roomId === room.id)
        .sort((a, b) => a.position - b.position)
        .map(toAssignment);
    },
  },

  {
    method: 'POST',
    path: '/teacher/rooms/:roomId/lesson-assignments',
    handler: (context) => {
      const user = teacher(context);
      const room = ownRoom(context.params['roomId'], user.id);
      const payload = body<Record<string, unknown>>(context);

      const lesson = ownLesson(String(payload['lessonId'] ?? ''), user.id);

      return mutate(() => {
        const existing = db().assignments.filter((a) => a.roomId === room.id);

        const assignment: DbAssignment = {
          id: newId('assign'),
          roomId: room.id,
          lessonId: lesson.id,
          position: Number(payload['position'] ?? existing.length + 1),
          status: (payload['status'] as DbAssignment['status']) ?? 'DRAFT',
          availableFrom: (payload['availableFrom'] as string | null) ?? null,
          dueAt: (payload['dueAt'] as string | null) ?? null,
          // Omitidos usam os padrões da §7.3; `null` explícito é "sem limite".
          timeLimitMinutes:
            'timeLimitMinutes' in payload ? (payload['timeLimitMinutes'] as number | null) : 30,
          maxAttempts: 'maxAttempts' in payload ? (payload['maxAttempts'] as number | null) : 3,
          questionCount: (payload['questionCount'] as number | null) ?? null,
          shuffleQuestions: payload['shuffleQuestions'] !== false,
          shuffleOptions: payload['shuffleOptions'] !== false,
          version: 1,
        };

        validateAssignment(assignment, lesson.id);

        db().assignments.push(assignment);
        room.everUsed = true;
        return toAssignment(assignment);
      });
    },
  },

  {
    method: 'PATCH',
    path: '/teacher/rooms/:roomId/lesson-assignments/:assignmentId',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      const assignment = db().assignments.find(
        (a) => a.id === context.params['assignmentId'] && a.roomId === room.id,
      );
      if (!assignment) {
        throw notFound('Atribuição não encontrada.');
      }

      const payload = body<Record<string, unknown>>(context);
      checkVersion(assignment.version, payload['version']);

      return mutate(() => {
        if (typeof payload['status'] === 'string') {
          assignment.status = payload['status'] as DbAssignment['status'];
        }
        if ('availableFrom' in payload) {
          assignment.availableFrom = (payload['availableFrom'] as string | null) ?? null;
        }
        if ('dueAt' in payload) {
          assignment.dueAt = (payload['dueAt'] as string | null) ?? null;
        }
        if ('timeLimitMinutes' in payload) {
          assignment.timeLimitMinutes = payload['timeLimitMinutes'] as number | null;
        }
        if ('maxAttempts' in payload) {
          assignment.maxAttempts = payload['maxAttempts'] as number | null;
        }
        if ('questionCount' in payload) {
          assignment.questionCount = payload['questionCount'] as number | null;
        }
        if (typeof payload['shuffleQuestions'] === 'boolean') {
          assignment.shuffleQuestions = payload['shuffleQuestions'];
        }
        if (typeof payload['shuffleOptions'] === 'boolean') {
          assignment.shuffleOptions = payload['shuffleOptions'];
        }

        validateAssignment(assignment, assignment.lessonId);

        assignment.version++;
        return toAssignment(assignment);
      });
    },
  },

  {
    method: 'DELETE',
    path: '/teacher/rooms/:roomId/lesson-assignments/:assignmentId',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      const assignment = db().assignments.find(
        (a) => a.id === context.params['assignmentId'] && a.roomId === room.id,
      );
      if (!assignment) {
        throw notFound('Atribuição não encontrada.');
      }

      if (db().attempts.some((a) => a.assignmentId === assignment.id)) {
        throw conflict(
          'ASSIGNMENT_IN_USE',
          'Alunos já iniciaram tentativas nesta lição. Arquive a atribuição em vez de retirá-la.',
        );
      }

      return mutate(() => {
        db().assignments = db().assignments.filter((a) => a.id !== assignment.id);
        renumber(
          db()
            .assignments.filter((a) => a.roomId === room.id)
            .sort((a, b) => a.position - b.position),
        );
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'PUT',
    path: '/teacher/rooms/:roomId/lesson-assignments/order',
    handler: (context) => {
      const room = ownRoom(context.params['roomId'], teacher(context).id);
      const { assignmentIds } = body<{ assignmentIds: string[] }>(context);

      return mutate(() => {
        assignmentIds.forEach((id, index) => {
          const assignment = db().assignments.find((a) => a.id === id && a.roomId === room.id);
          if (assignment) {
            assignment.position = index + 1;
          }
        });

        return db()
          .assignments.filter((a) => a.roomId === room.id)
          .sort((a, b) => a.position - b.position)
          .map(toAssignment);
      });
    },
  },

  {
    method: 'GET',
    path: '/teacher/reports/overview',
    handler: (context) => {
      const user = teacher(context);
      const scope = reportScope(context, user.id);

      const submitted = scope.attempts.filter((a) => a.status !== 'IN_PROGRESS');
      const averageScore =
        submitted.length === 0
          ? null
          : Math.round(submitted.reduce((sum, a) => sum + a.scorePercent, 0) / submitted.length);

      const buckets = [
        { label: '0–49%', min: 0, max: 49 },
        { label: '50–69%', min: 50, max: 69 },
        { label: '70–89%', min: 70, max: 89 },
        { label: '90–100%', min: 90, max: 100 },
      ];

      return {
        metrics: {
          studentCount: scope.studentIds.length,
          activeStudentCount: scope.studentIds.filter((id) =>
            scope.attempts.some((a) => a.studentId === id),
          ).length,
          attemptCount: scope.attempts.length,
          submittedAttemptCount: submitted.length,
          averageScorePercent: averageScore,
          passRatePercent:
            submitted.length === 0
              ? null
              : Math.round(
                  (submitted.filter((a) => a.passed).length / submitted.length) * 100,
                ),
          completionPercent:
            scope.assignments.length === 0 || scope.studentIds.length === 0
              ? null
              : Math.round(
                  (scope.rooms.reduce(
                    (sum, room) =>
                      sum +
                      activeStudentsOf(room.id).reduce(
                        (inner, studentId) => inner + lessonsPassedOf(room.id, studentId, room),
                        0,
                      ),
                    0,
                  ) /
                    (scope.assignments.length * scope.studentIds.length)) *
                    100,
                ),
        },
        attemptsOverTime: attemptsSeries(scope.attempts),
        scoreDistribution: buckets.map((bucket) => ({
          label: bucket.label,
          count: submitted.filter(
            (a) => a.scorePercent >= bucket.min && a.scorePercent <= bucket.max,
          ).length,
        })),
        lessonCompletion: scope.assignments.map((assignment) => {
          const room = db().rooms.find((r) => r.id === assignment.roomId) as DbRoom;
          const students = activeStudentsOf(assignment.roomId);
          const completed = students.filter((studentId) =>
            isPassed(bestAttemptOf(assignment.id, studentId), room),
          ).length;

          const scores = students
            .map((studentId) => bestAttemptOf(assignment.id, studentId))
            .filter((attempt): attempt is NonNullable<typeof attempt> => attempt !== null)
            .map((attempt) => attempt.scorePercent);

          return {
            lessonId: assignment.lessonId,
            lessonTitle: db().lessons.find((l) => l.id === assignment.lessonId)?.title ?? 'Lição',
            completedStudents: completed,
            totalStudents: students.length,
            completionPercent:
              students.length === 0 ? 0 : Math.round((completed / students.length) * 100),
            averageScorePercent:
              scores.length === 0
                ? null
                : Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length),
          };
        }),
        generatedAt: nowIso(),
      };
    },
  },

  {
    method: 'GET',
    path: '/teacher/reports/students',
    handler: (context) => {
      const user = teacher(context);
      const scope = reportScope(context, user.id);

      const rows = scope.studentIds.map((studentId) => {
        const student = db().users.find((u) => u.id === studentId);
        const attempts = scope.attempts.filter(
          (a) => a.studentId === studentId && a.status !== 'IN_PROGRESS',
        );
        const scores = attempts.map((a) => a.scorePercent);

        const completed = scope.rooms.reduce(
          (sum, room) => sum + lessonsPassedOf(room.id, studentId, room),
          0,
        );
        const xp = scope.rooms.reduce((sum, room) => sum + roomXpOf(room.id, studentId), 0);
        const stars = scope.rooms.reduce((sum, room) => sum + roomStarsOf(room.id, studentId), 0);

        return {
          studentId,
          fullName: student?.fullName ?? 'Aluno',
          registrationNumber: student?.registrationNumber ?? null,
          attemptCount: attempts.length,
          averageScorePercent:
            scores.length === 0
              ? null
              : Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length),
          bestScorePercent: scores.length === 0 ? null : Math.max(...scores),
          completedLessons: completed,
          totalLessons: scope.assignments.length,
          xp,
          stars,
          lastActivityAt:
            attempts
              .map((a) => a.submittedAt ?? a.startedAt)
              .sort()
              .pop() ?? null,
        };
      });

      return paginate(rows, context);
    },
  },

  {
    method: 'GET',
    path: '/teacher/reports/ranking',
    handler: (context) => {
      const user = teacher(context);
      const scope = reportScope(context, user.id);

      const rows = scope.studentIds
        .map((studentId) => {
          const student = db().users.find((u) => u.id === studentId);
          return {
            studentId,
            // O professor vê nomes completos das próprias salas — §10 da spec.
            fullName: student?.fullName ?? 'Aluno',
            registrationNumber: student?.registrationNumber ?? null,
            xp: scope.rooms.reduce((sum, room) => sum + roomXpOf(room.id, studentId), 0),
            stars: scope.rooms.reduce((sum, room) => sum + roomStarsOf(room.id, studentId), 0),
            completedLessons: scope.rooms.reduce(
              (sum, room) => sum + lessonsPassedOf(room.id, studentId, room),
              0,
            ),
          };
        })
        .sort((a, b) => b.xp - a.xp || b.stars - a.stars)
        .map((row, index) => ({ position: index + 1, ...row }));

      return paginate(rows, context);
    },
  },

  {
    method: 'GET',
    path: '/teacher/reports/students/:studentId/attempts',
    handler: (context) => {
      const user = teacher(context);
      const scope = reportScope(context, user.id);
      const studentId = context.params['studentId'];

      const rows = scope.attempts
        .filter((attempt) => attempt.studentId === studentId)
        .map((attempt, index) => {
          const assignment = db().assignments.find((a) => a.id === attempt.assignmentId);
          return {
            attemptId: attempt.id,
            lessonId: assignment?.lessonId ?? '',
            lessonTitle: db().lessons.find((l) => l.id === assignment?.lessonId)?.title ?? 'Lição',
            attemptNumber: index + 1,
            status: attempt.status,
            scorePercent: attempt.status === 'IN_PROGRESS' ? null : attempt.scorePercent,
            passed: attempt.status === 'IN_PROGRESS' ? null : attempt.passed,
            startedAt: attempt.startedAt,
            submittedAt: attempt.submittedAt,
          };
        });

      return paginate(rows, context);
    },
  },

  {
    method: 'GET',
    path: '/teacher/reports/export.csv',
    handler: (context) => {
      const user = teacher(context);
      const scope = reportScope(context, user.id);

      // O CSV é gerado no servidor — o frontend não monta arquivo.
      const header = 'aluno;matricula;tentativas;media;melhor;licoes_concluidas;xp\n';
      const lines = scope.studentIds.map((studentId) => {
        const student = db().users.find((u) => u.id === studentId);
        const attempts = scope.attempts.filter(
          (a) => a.studentId === studentId && a.status !== 'IN_PROGRESS',
        );
        const scores = attempts.map((a) => a.scorePercent);
        const average =
          scores.length === 0
            ? ''
            : Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length);

        return [
          student?.fullName ?? '',
          student?.registrationNumber ?? '',
          attempts.length,
          average,
          scores.length === 0 ? '' : Math.max(...scores),
          scope.rooms.reduce((sum, room) => sum + lessonsPassedOf(room.id, studentId, room), 0),
          scope.rooms.reduce((sum, room) => sum + roomXpOf(room.id, studentId), 0),
        ].join(';');
      });

      return new Blob([header + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    },
  },
];

/** Recorte do relatório: salas, atribuições, alunos e tentativas no período. */
function reportScope(context: MockContext, teacherId: string) {
  const roomId = context.query.get('roomId');
  const from = context.query.get('from');
  const to = context.query.get('to');

  const rooms = db()
    .rooms.filter((room) => room.teacherId === teacherId)
    .filter((room) => (roomId ? room.id === roomId : true));

  const assignments = rooms.flatMap((room) => assignmentsOfRoom(room.id));
  const assignmentIds = new Set(assignments.map((a) => a.id));
  const studentIds = [...new Set(rooms.flatMap((room) => activeStudentsOf(room.id)))];

  const attempts = db().attempts.filter((attempt) => {
    if (!assignmentIds.has(attempt.assignmentId)) {
      return false;
    }
    const at = Date.parse(attempt.submittedAt ?? attempt.startedAt);
    if (from && at < Date.parse(from)) {
      return false;
    }
    if (to && at > Date.parse(to)) {
      return false;
    }
    return true;
  });

  return { rooms, assignments, studentIds, attempts };
}

function attemptsSeries(attempts: { startedAt: string; submittedAt: string | null }[]) {
  const byDay = new Map<string, { attempts: number; submitted: number }>();

  for (const attempt of attempts) {
    const day = attempt.startedAt.slice(0, 10);
    const entry = byDay.get(day) ?? { attempts: 0, submitted: 0 };
    entry.attempts++;
    if (attempt.submittedAt) {
      entry.submitted++;
    }
    byDay.set(day, entry);
  }

  return [...byDay.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, counts]) => ({ date, ...counts }));
}

function buildQuestion(
  lessonId: string,
  payload: Record<string, unknown>,
  order: number,
): DbQuestion {
  const question: DbQuestion = {
    id: newId('q'),
    lessonId,
    type: (payload['type'] as DbQuestion['type']) ?? 'SINGLE_CHOICE',
    prompt: String(payload['prompt'] ?? ''),
    explanation: String(payload['explanation'] ?? ''),
    order,
    active: true,
    options: [],
    correctBoolean: null,
    correctNumericValue: null,
    absoluteTolerance: null,
    unit: 'NONE',
    decimalPlaces: 0,
    version: 1,
  };

  applyQuestionPayload(question, payload);
  return question;
}

function applyQuestionPayload(question: DbQuestion, payload: Record<string, unknown>): void {
  if (typeof payload['prompt'] === 'string') {
    question.prompt = payload['prompt'];
  }
  if ('explanation' in payload) {
    question.explanation = String(payload['explanation'] ?? '');
  }
  if (typeof payload['type'] === 'string') {
    question.type = payload['type'] as DbQuestion['type'];
  }

  if (Array.isArray(payload['options'])) {
    question.options = (payload['options'] as { id: string | null; text: string; correct: boolean }[]).map(
      (option) => ({ id: option.id ?? newId('opt'), text: option.text, correct: option.correct }),
    );
  }
  if ('correctBoolean' in payload) {
    question.correctBoolean = payload['correctBoolean'] as boolean | null;
  }
  if ('correctNumericValue' in payload) {
    question.correctNumericValue = payload['correctNumericValue'] as string | null;
  }
  if ('absoluteTolerance' in payload) {
    question.absoluteTolerance = payload['absoluteTolerance'] as string | null;
  }
  if ('unit' in payload) {
    question.unit = (payload['unit'] as DbQuestion['unit']) ?? 'NONE';
  }
  if ('decimalPlaces' in payload) {
    question.decimalPlaces = (payload['decimalPlaces'] as number | null) ?? 0;
  }

  validateQuestion(question);
}

/** Regras da §7.2 da spec, aplicadas no servidor. */
function validateQuestion(question: DbQuestion): void {
  if (!question.prompt.trim()) {
    throw unprocessable('Verifique os campos destacados.', [
      { field: 'prompt', message: 'O enunciado é obrigatório.' },
    ]);
  }

  const correct = question.options.filter((option) => option.correct).length;

  if (question.type === 'SINGLE_CHOICE' && correct !== 1) {
    throw unprocessable('Verifique os campos destacados.', [
      { field: 'options', message: 'Escolha única exige exatamente uma alternativa correta.' },
    ]);
  }
  if (question.type === 'MULTIPLE_CHOICE' && correct < 2) {
    throw unprocessable('Verifique os campos destacados.', [
      { field: 'options', message: 'Múltipla escolha exige duas ou mais alternativas corretas.' },
    ]);
  }
  if (question.type === 'TRUE_FALSE' && question.correctBoolean === null) {
    throw unprocessable('Verifique os campos destacados.', [
      { field: 'correctBoolean', message: 'Informe se a afirmação é verdadeira ou falsa.' },
    ]);
  }
  if (question.type === 'NUMERIC' && !question.correctNumericValue) {
    throw unprocessable('Verifique os campos destacados.', [
      { field: 'correctNumericValue', message: 'Informe o valor esperado.' },
    ]);
  }
}

function validateAssignment(assignment: DbAssignment, lessonId: string): void {
  if (assignment.availableFrom && assignment.dueAt) {
    if (Date.parse(assignment.dueAt) <= Date.parse(assignment.availableFrom)) {
      throw unprocessable('Verifique os campos destacados.', [
        { field: 'dueAt', message: 'O prazo precisa ser depois da abertura.' },
      ]);
    }
  }

  // Publicação exige questões suficientes — §7.3 da spec.
  if (assignment.status === 'PUBLISHED') {
    const available = questionsOf(lessonId).length;
    if (available === 0) {
      throw unprocessable('Verifique os campos destacados.', [
        { field: 'questionCount', message: 'Esta lição não tem questões ativas.' },
      ]);
    }
    if (assignment.questionCount !== null && assignment.questionCount > available) {
      throw unprocessable('Verifique os campos destacados.', [
        {
          field: 'questionCount',
          message: `A lição tem apenas ${available} ${available === 1 ? 'questão ativa' : 'questões ativas'}.`,
        },
      ]);
    }
  }
}

/** Reexportado para o painel de cenários. */
export { isPast };
