import type {
  DbAssignment,
  DbAttempt,
  DbQuestion,
  DbQuestionSnapshot,
  DbRoom,
} from './db';
import { db } from './db';

/**
 * Regras de negócio do "servidor" — Parte 7, §1, item 4.
 *
 * Este arquivo existe para o frontend **não** precisar existir. Toda conta que
 * a §11 da spec de integração proíbe no cliente — nota, estrelas, XP,
 * aprovação, desbloqueio — mora aqui, do lado do provedor, exatamente como
 * aconteceria no backend real.
 *
 * As fórmulas seguem a §6.3 da spec de integração.
 */

/** Faixas de estrelas: <50 = 0, 50–69 = 1, 70–89 = 2, 90–100 = 3. */
export function starsFor(scorePercent: number): number {
  if (scorePercent < 50) {
    return 0;
  }
  if (scorePercent < 70) {
    return 1;
  }
  return scorePercent < 90 ? 2 : 3;
}

/** XP potencial de uma tentativa: acertos × 10. */
export function xpFor(correctAnswers: number): number {
  return correctAnswers * 10;
}

export function scorePercentFor(correct: number, total: number): number {
  return total === 0 ? 0 : Math.round((correct / total) * 100);
}

/** Tentativas do aluno numa atribuição, da mais antiga para a mais recente. */
export function attemptsOf(assignmentId: string, studentId: string): DbAttempt[] {
  return db()
    .attempts.filter((a) => a.assignmentId === assignmentId && a.studentId === studentId)
    .sort((a, b) => Date.parse(a.startedAt) - Date.parse(b.startedAt));
}

/** Melhor tentativa encerrada — é ela que define aprovação e estrelas. */
export function bestAttemptOf(assignmentId: string, studentId: string): DbAttempt | null {
  const finished = attemptsOf(assignmentId, studentId).filter((a) => a.status !== 'IN_PROGRESS');
  if (finished.length === 0) {
    return null;
  }
  return finished.reduce((best, current) =>
    current.scorePercent > best.scorePercent ? current : best,
  );
}

export function activeAttemptOf(assignmentId: string, studentId: string): DbAttempt | null {
  return (
    attemptsOf(assignmentId, studentId).find((a) => a.status === 'IN_PROGRESS') ?? null
  );
}

/** Tentativas extras concedidas pelo professor. */
export function extraAttemptsOf(assignmentId: string, studentId: string): number {
  return (
    db().extraAttempts.find(
      (e) => e.assignmentId === assignmentId && e.studentId === studentId,
    )?.quantity ?? 0
  );
}

/** `null` quando a atribuição não limita tentativas. */
export function attemptsRemaining(
  assignment: DbAssignment,
  studentId: string,
): number | null {
  if (assignment.maxAttempts === null) {
    return null;
  }
  const used = attemptsOf(assignment.id, studentId).length;
  const allowed = assignment.maxAttempts + extraAttemptsOf(assignment.id, studentId);
  return Math.max(0, allowed - used);
}

export function isPassed(attempt: DbAttempt | null, room: DbRoom): boolean {
  return attempt !== null && attempt.scorePercent >= room.passingScorePercent;
}

export type Availability = 'AVAILABLE' | 'IN_PROGRESS' | 'PASSED' | 'FAILED' | 'LOCKED';
export type LockReason =
  | 'PREREQUISITE_NOT_PASSED'
  | 'NOT_YET_AVAILABLE'
  | 'DUE_DATE_PASSED'
  | 'NO_ATTEMPTS_LEFT'
  | 'NOT_PUBLISHED';

export interface AvailabilityResult {
  availability: Availability;
  lockReason: LockReason | null;
}

/**
 * Decide a situação do aluno numa atribuição.
 *
 * A ordem das verificações importa e é a mesma da spec: publicação, janela de
 * disponibilidade, pré-requisito e tentativas. O primeiro impedimento
 * encontrado é o que o aluno vê — mostrar todos de uma vez confundiria.
 */
export function availabilityFor(
  assignment: DbAssignment,
  room: DbRoom,
  studentId: string,
  previous: DbAssignment | null,
): AvailabilityResult {
  const now = Date.now();

  if (assignment.status !== 'PUBLISHED') {
    return { availability: 'LOCKED', lockReason: 'NOT_PUBLISHED' };
  }

  if (assignment.availableFrom && Date.parse(assignment.availableFrom) > now) {
    return { availability: 'LOCKED', lockReason: 'NOT_YET_AVAILABLE' };
  }

  const best = bestAttemptOf(assignment.id, studentId);
  const passed = isPassed(best, room);

  // Pré-requisito: a lição anterior precisa estar aprovada. Já ter passado
  // nesta libera a revisão mesmo que a anterior tenha sido esquecida.
  if (previous && !passed) {
    const previousBest = bestAttemptOf(previous.id, studentId);
    if (!isPassed(previousBest, room)) {
      return { availability: 'LOCKED', lockReason: 'PREREQUISITE_NOT_PASSED' };
    }
  }

  if (activeAttemptOf(assignment.id, studentId)) {
    return { availability: 'IN_PROGRESS', lockReason: null };
  }

  if (assignment.dueAt && Date.parse(assignment.dueAt) < now) {
    // Prazo encerrado não apaga o que já foi conquistado.
    if (best) {
      return { availability: passed ? 'PASSED' : 'FAILED', lockReason: null };
    }
    return { availability: 'LOCKED', lockReason: 'DUE_DATE_PASSED' };
  }

  const remaining = attemptsRemaining(assignment, studentId);
  if (remaining === 0) {
    return best
      ? { availability: passed ? 'PASSED' : 'FAILED', lockReason: null }
      : { availability: 'LOCKED', lockReason: 'NO_ATTEMPTS_LEFT' };
  }

  if (best) {
    return { availability: passed ? 'PASSED' : 'FAILED', lockReason: null };
  }

  return { availability: 'AVAILABLE', lockReason: null };
}

/** O aluno pode iniciar uma tentativa nova agora? */
export function canStartAttempt(
  assignment: DbAssignment,
  room: DbRoom,
  studentId: string,
  previous: DbAssignment | null,
): { allowed: boolean; reason: LockReason | null } {
  const { availability, lockReason } = availabilityFor(assignment, room, studentId, previous);

  if (availability === 'LOCKED') {
    return { allowed: false, reason: lockReason };
  }
  if (assignment.dueAt && Date.parse(assignment.dueAt) < Date.now()) {
    return { allowed: false, reason: 'DUE_DATE_PASSED' };
  }
  if (attemptsRemaining(assignment, studentId) === 0) {
    return { allowed: false, reason: 'NO_ATTEMPTS_LEFT' };
  }
  return { allowed: true, reason: null };
}

/** XP do aluno na sala: soma do melhor resultado de cada atribuição. */
export function roomXpOf(roomId: string, studentId: string): number {
  return assignmentsOfRoom(roomId).reduce((total, assignment) => {
    const best = bestAttemptOf(assignment.id, studentId);
    return total + (best ? xpFor(best.correctAnswers) : 0);
  }, 0);
}

export function roomStarsOf(roomId: string, studentId: string): number {
  return assignmentsOfRoom(roomId).reduce((total, assignment) => {
    const best = bestAttemptOf(assignment.id, studentId);
    return total + (best?.stars ?? 0);
  }, 0);
}

export function lessonsPassedOf(roomId: string, studentId: string, room: DbRoom): number {
  return assignmentsOfRoom(roomId).filter((assignment) =>
    isPassed(bestAttemptOf(assignment.id, studentId), room),
  ).length;
}

/** Nível: 100 XP por nível. */
export function levelOf(xp: number): number {
  return Math.floor(xp / 100) + 1;
}

export function levelProgressOf(xp: number): number {
  return xp % 100;
}

/** Atribuições publicadas da sala, na ordem da trilha. */
export function assignmentsOfRoom(roomId: string): DbAssignment[] {
  return db()
    .assignments.filter((a) => a.roomId === roomId && a.status === 'PUBLISHED')
    .sort((a, b) => a.position - b.position);
}

/**
 * Sorteia e congela as questões de uma tentativa.
 *
 * O *snapshot* é o que permite editar a questão original depois sem alterar
 * tentativas já feitas — e é por isso que a correção usa os dados congelados.
 */
export function buildSnapshots(assignment: DbAssignment): DbQuestionSnapshot[] {
  const pool = db()
    .questions.filter((q) => q.lessonId === assignment.lessonId && q.active)
    .sort((a, b) => a.order - b.order);

  const selected = assignment.shuffleQuestions ? shuffle(pool) : pool;
  const count = assignment.questionCount ?? selected.length;

  return selected.slice(0, count).map((question, index) => ({
    id: `snap-${question.id}-${index}-${Math.random().toString(36).slice(2, 7)}`,
    questionId: question.id,
    order: index + 1,
    type: question.type,
    prompt: question.prompt,
    explanation: question.explanation,
    options: assignment.shuffleOptions ? shuffle(question.options) : [...question.options],
    correctBoolean: question.correctBoolean,
    correctNumericValue: question.correctNumericValue,
    absoluteTolerance: question.absoluteTolerance,
    unit: question.unit,
    decimalPlaces: question.decimalPlaces,
  }));
}

/**
 * Corrige uma resposta contra o *snapshot*.
 *
 * `MULTIPLE_CHOICE` exige a seleção **exata** (§7.2 da spec). `NUMERIC`
 * compara com tolerância absoluta.
 */
export function isAnswerCorrect(
  snapshot: DbQuestionSnapshot,
  answer: {
    selectedOptionIds?: string[];
    booleanValue?: boolean;
    numericValue?: string;
  },
): boolean {
  switch (snapshot.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE': {
      const correctIds = snapshot.options.filter((o) => o.correct).map((o) => o.id).sort();
      const chosen = [...(answer.selectedOptionIds ?? [])].sort();
      return (
        correctIds.length === chosen.length &&
        correctIds.every((id, index) => id === chosen[index])
      );
    }
    case 'TRUE_FALSE':
      return answer.booleanValue === snapshot.correctBoolean;
    case 'NUMERIC': {
      if (answer.numericValue === undefined || snapshot.correctNumericValue === null) {
        return false;
      }
      const given = Number(answer.numericValue);
      const expected = Number(snapshot.correctNumericValue);
      const tolerance = Number(snapshot.absoluteTolerance ?? '0');
      return Number.isFinite(given) && Math.abs(given - expected) <= tolerance;
    }
  }
}

/** Fecha a tentativa: corrige, pontua e grava o resultado. */
export function gradeAttempt(attempt: DbAttempt, room: DbRoom, expired: boolean): void {
  // Questões sem resposta contam como incorretas (§6.3 da spec).
  const correct = attempt.answers.filter((a) => a.correct).length;
  const total = attempt.questions.length;

  attempt.correctAnswers = correct;
  attempt.scorePercent = scorePercentFor(correct, total);
  attempt.stars = starsFor(attempt.scorePercent);
  attempt.passed = attempt.scorePercent >= room.passingScorePercent;
  attempt.status = expired ? 'EXPIRED' : 'SUBMITTED';
  attempt.submittedAt = new Date().toISOString();

  // Só a melhoria do melhor resultado gera XP novo.
  const previousBest = attemptsOf(attempt.assignmentId, attempt.studentId)
    .filter((a) => a.id !== attempt.id && a.status !== 'IN_PROGRESS')
    .reduce<DbAttempt | null>(
      (best, current) => (best === null || current.correctAnswers > best.correctAnswers ? current : best),
      null,
    );

  const previousXp = previousBest ? xpFor(previousBest.correctAnswers) : 0;
  attempt.xpEarned = Math.max(0, xpFor(correct) - previousXp);
}

export function questionById(id: string): DbQuestion | undefined {
  return db().questions.find((q) => q.id === id);
}

function shuffle<T>(items: readonly T[]): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}
