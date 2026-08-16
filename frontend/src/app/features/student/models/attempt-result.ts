import type { AttemptStatus } from '../../../core/models/enums';
import type { AnswerPayload, AttemptQuestion } from './attempt-question';

/**
 * Tentativa **encerrada** — Parte 4, §6.1 e §6.6.
 *
 * Esta é a metade "com gabarito". Ela importa `AttemptQuestion`, mas o inverso
 * nunca acontece: `attempt-question.ts` não conhece nada daqui. Só as telas de
 * resultado importam este arquivo.
 *
 * Nenhum valor abaixo é calculado pelo frontend. `scorePercent`, `passed`,
 * `stars`, `xpEarnedThisAttempt` e `roomXpTotal` vêm prontos da API e são
 * apenas apresentados (§11 da spec de integração).
 */

/** Revisão de uma questão depois da correção. */
export interface ResultAnswer {
  question: AttemptQuestion;
  /** `null` quando o aluno não respondeu — conta como incorreta (§6.3 da spec). */
  studentAnswer: AnswerPayload | null;
  correctAnswer: AnswerPayload;
  correct: boolean;
  explanation: string;
}

/** `GET /student/attempts/{attemptId}/result`. */
export interface AttemptResult {
  attemptId: string;
  assignmentId: string;
  roomId: string;
  lessonId: string;
  lessonTitle: string;
  status: AttemptStatus;
  correctAnswers: number;
  totalQuestions: number;
  scorePercent: number;
  passed: boolean;
  stars: number;
  xpEarnedThisAttempt: number;
  roomXpTotal: number;
  startedAt: string;
  submittedAt: string | null;
  /** Nota mínima da sala, para explicar a aprovação. Padrão 50% (§6.3 da spec). */
  passingScorePercent: number;
  /** `null` quando a atribuição não limita tentativas. */
  attemptsRemaining: number | null;
  answers: ResultAnswer[];
}
