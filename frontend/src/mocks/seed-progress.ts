import { db, persist, type DbAttempt } from './db';
import { assignmentsOfRoom, buildSnapshots, gradeAttempt, isAnswerCorrect } from './rules';
import { SEED_IDS } from './seed';

/**
 * Progresso de exemplo — Parte 7, §1.
 *
 * Roda uma única vez, depois da semente. Existe porque um mock só de estado
 * inicial não exercita ranking, conquistas nem histórico de tentativas: as três
 * telas ficariam vazias e ninguém veria se funcionam.
 *
 * As tentativas são **corrigidas pelas mesmas regras** que a API do mock usa
 * (`gradeAttempt`), e não com notas escritas à mão. Uma nota inventada aqui
 * poderia divergir da lógica real e esconder um defeito.
 */

interface ProgressPlan {
  studentId: string;
  roomId: string;
  /** Quantas atribuições da trilha percorrer, na ordem. */
  lessons: number;
  /** Proporção de acertos por lição, de 0 a 1. */
  accuracy: number[];
}

const PLANS: ProgressPlan[] = [
  // Carla vai bem e destrava boa parte da trilha.
  { studentId: SEED_IDS.STUDENT_CARLA_ID, roomId: SEED_IDS.ROOM_2A_ID, lessons: 3, accuracy: [1, 0.7, 0.7] },
  // Diego empaca na segunda lição: exercita o estado "não aprovada".
  { studentId: SEED_IDS.STUDENT_DIEGO_ID, roomId: SEED_IDS.ROOM_2A_ID, lessons: 2, accuracy: [0.75, 0.34] },
  // Elisa mal começou.
  { studentId: SEED_IDS.STUDENT_ELISA_ID, roomId: SEED_IDS.ROOM_2A_ID, lessons: 1, accuracy: [0.5] },
  { studentId: SEED_IDS.STUDENT_CARLA_ID, roomId: SEED_IDS.ROOM_3B_ID, lessons: 1, accuracy: [1] },
];

/**
 * A guarda é o **estado do banco**, não um sinalizador de módulo.
 *
 * Um sinalizador ficaria preso depois de `resetDatabase()` e o banco recomeçaria
 * sem progresso nenhum — que foi exatamente o que quebrou os testes de
 * "atribuição já usada". Perguntar ao banco é sempre verdade.
 */
export function seedProgressOnce(): void {
  if (db().attempts.length > 0) {
    return;
  }

  for (const plan of PLANS) {
    const room = db().rooms.find((r) => r.id === plan.roomId);
    if (!room) {
      continue;
    }

    const assignments = assignmentsOfRoom(plan.roomId).slice(0, plan.lessons);

    assignments.forEach((assignment, index) => {
      const questions = buildSnapshots(assignment);
      if (questions.length === 0) {
        return;
      }

      const accuracy = plan.accuracy[index] ?? 0.5;
      const targetCorrect = Math.round(questions.length * accuracy);

      const attempt: DbAttempt = {
        id: `seed-attempt-${plan.studentId}-${assignment.id}`,
        assignmentId: assignment.id,
        studentId: plan.studentId,
        status: 'IN_PROGRESS',
        startedAt: new Date(Date.now() - (index + 2) * 86_400_000).toISOString(),
        expiresAt: null,
        submittedAt: null,
        questions,
        answers: [],
        correctAnswers: 0,
        scorePercent: 0,
        passed: false,
        stars: 0,
        xpEarned: 0,
        idempotencyKey: null,
      };

      // Responde acertando as `targetCorrect` primeiras e errando o resto.
      questions.forEach((snapshot, position) => {
        const shouldHit = position < targetCorrect;
        const answer = shouldHit ? correctPayload(snapshot) : wrongPayload(snapshot);

        attempt.answers.push({
          questionSnapshotId: snapshot.id,
          answeredAt: attempt.startedAt,
          selectedOptionIds: answer.selectedOptionIds ?? null,
          booleanValue: answer.booleanValue ?? null,
          numericValue: answer.numericValue ?? null,
          correct: isAnswerCorrect(snapshot, answer),
        });
      });

      db().attempts.push(attempt);
      gradeAttempt(attempt, room, false);
    });
  }

  persist();
}

interface Payload {
  selectedOptionIds?: string[];
  booleanValue?: boolean;
  numericValue?: string;
}

function correctPayload(snapshot: DbAttempt['questions'][number]): Payload {
  switch (snapshot.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return { selectedOptionIds: snapshot.options.filter((o) => o.correct).map((o) => o.id) };
    case 'TRUE_FALSE':
      return { booleanValue: snapshot.correctBoolean ?? true };
    case 'NUMERIC':
      return { numericValue: snapshot.correctNumericValue ?? '0' };
  }
}

function wrongPayload(snapshot: DbAttempt['questions'][number]): Payload {
  switch (snapshot.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE': {
      const wrong = snapshot.options.find((o) => !o.correct);
      return { selectedOptionIds: wrong ? [wrong.id] : [] };
    }
    case 'TRUE_FALSE':
      return { booleanValue: !(snapshot.correctBoolean ?? true) };
    case 'NUMERIC':
      return { numericValue: '-1' };
  }
}
