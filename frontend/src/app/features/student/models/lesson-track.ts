import type { AttemptStatus } from '../../../core/models/enums';
import type { StudentMaterial } from './media';

/**
 * Trilha e lição do aluno — §6.2 da spec de integração e Parte 4, §5.
 *
 * **A trilha nunca decide sozinha que a próxima lição está liberada.** Tanto
 * `availability` quanto `lockReason` vêm da API; a tela só traduz o código para
 * português e escolhe o ícone. Não existe aqui nenhuma comparação de data,
 * contagem de tentativas ou verificação de pré-requisito.
 */

/** Situação do aluno na lição, decidida pela API. */
export type LessonAvailability =
  | 'AVAILABLE'
  | 'IN_PROGRESS'
  | 'PASSED'
  | 'FAILED'
  | 'LOCKED';

/** Motivo do bloqueio — §6.2 da spec. Cada código tem texto próprio na tela. */
export type LessonLockReason =
  | 'PREREQUISITE_NOT_PASSED'
  | 'NOT_YET_AVAILABLE'
  | 'DUE_DATE_PASSED'
  | 'NO_ATTEMPTS_LEFT'
  | 'NOT_PUBLISHED';

/**
 * Regras da atribuição, mostradas antes de o aluno começar (Parte 4, §5.2).
 *
 * `timeLimitMinutes` e `maxAttempts` nulos significam **sem limite** — a tela
 * escreve exatamente isso, nunca um número inventado (§7.3 da spec).
 */
export interface LessonRules {
  timeLimitMinutes: number | null;
  maxAttempts: number | null;
  attemptsUsed: number;
  /** `null` quando não há limite de tentativas. */
  attemptsRemaining: number | null;
  questionCount: number;
  passingScorePercent: number;
}

/** Item de `GET /student/rooms/{roomId}/lessons`. */
export interface LessonTrackItem {
  assignmentId: string;
  lessonId: string;
  title: string;
  summary: string | null;
  order: number;
  availability: LessonAvailability;
  lockReason: LessonLockReason | null;
  availableFrom: string | null;
  dueAt: string | null;
  rules: LessonRules;
  /** Melhor nota do aluno; define aprovação e estrelas (§6.3 da spec). */
  bestScorePercent: number | null;
  stars: number | null;
  /** Tentativa em andamento, quando houver. */
  activeAttemptId: string | null;
  activeAttemptExpiresAt: string | null;
  /** Melhor tentativa, para o atalho "Ver resultado". */
  bestAttemptId: string | null;
}

/** `GET /student/rooms/{roomId}/lessons/{lessonId}`. */
export interface LessonDetail {
  assignmentId: string;
  lessonId: string;
  roomId: string;
  title: string;
  summary: string | null;
  theoryMarkdown: string;
  materials: StudentMaterial[];
  availability: LessonAvailability;
  lockReason: LessonLockReason | null;
  availableFrom: string | null;
  dueAt: string | null;
  rules: LessonRules;
  bestScorePercent: number | null;
  stars: number | null;
  activeAttemptId: string | null;
  bestAttemptId: string | null;
}

/** Item de `GET /student/rooms/{roomId}/lessons/{lessonId}/attempts`. */
export interface AttemptHistoryItem {
  attemptId: string;
  status: AttemptStatus;
  startedAt: string;
  submittedAt: string | null;
  scorePercent: number | null;
  stars: number | null;
  passed: boolean;
  correctAnswers: number | null;
  totalQuestions: number;
  /** Marcado pela API: é esta tentativa que define aprovação e estrelas. */
  best: boolean;
}
