import type { ContentStatus } from '../../../core/models/enums';

/**
 * Atribuição de lição a uma sala — §7.3 da spec de integração.
 *
 * Os três campos "sem limite" são `null`, nunca zero e nunca um número
 * inventado pelo frontend: `timeLimitMinutes`, `maxAttempts` e `questionCount`.
 */
export interface LessonAssignment {
  id: string;
  roomId: string;
  lesson: {
    id: string;
    title: string;
    /** Questões ativas da lição — base do limite de `questionCount`. */
    activeQuestionCount: number;
  };
  position: number;
  status: ContentStatus;
  /** Instantes ISO 8601 UTC. A tela exibe em America/Sao_Paulo. */
  availableFrom: string | null;
  dueAt: string | null;
  /** `null` = sem limite de tempo. */
  timeLimitMinutes: number | null;
  /** `null` = tentativas ilimitadas. */
  maxAttempts: number | null;
  /** `null` = todas as questões ativas da lição. */
  questionCount: number | null;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  version: number;
  /**
   * A atribuição ainda é futura e pode ser retirada. Quem decide é a API; um
   * `DELETE` em atribuição já em uso responde `409`.
   */
  removable: boolean;
}

export interface AssignmentPayload {
  position?: number;
  status: ContentStatus;
  availableFrom: string | null;
  dueAt: string | null;
  timeLimitMinutes: number | null;
  maxAttempts: number | null;
  questionCount: number | null;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
}

export interface CreateAssignmentRequest extends AssignmentPayload {
  lessonId: string;
}

export interface PatchAssignmentRequest extends Partial<AssignmentPayload> {
  version: number;
}

/** `PUT /teacher/rooms/{roomId}/lesson-assignments/order`. */
export interface AssignmentOrderRequest {
  assignmentIds: string[];
}

/** Padrões de criação — §7.3 da spec: 30 minutos e 3 tentativas. */
export const DEFAULT_TIME_LIMIT_MINUTES = 30;
export const DEFAULT_MAX_ATTEMPTS = 3;
