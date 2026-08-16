import type { NumericUnit, QuestionType } from '../../../core/models/enums';

/** Opção de questão de escolha — §7.2 da spec de integração. */
export interface QuestionOption {
  id: string | null;
  text: string;
  correct: boolean;
}

/**
 * Questão na visão do autor.
 *
 * Este tipo carrega gabarito porque é a tela de **autoria**. Ele nunca é usado
 * em tentativa em andamento — lá o tipo é outro, sem `correct`, sem
 * `explanation` e sem `correctBoolean` (princípio 2 da visão geral).
 */
export interface Question {
  id: string;
  lessonId: string;
  prompt: string;
  type: QuestionType;
  explanation: string | null;
  order: number;
  /** Questão já usada em tentativas pode ser arquivada em vez de excluída. */
  archived: boolean;
  version: number;

  options: QuestionOption[];
  correctBoolean: boolean | null;
  /** String decimal, nunca `float` — §2.1 da spec. */
  correctNumericValue: string | null;
  absoluteTolerance: string | null;
  unit: NumericUnit | null;
  decimalPlaces: number | null;
}

export interface QuestionPayload {
  prompt: string;
  type: QuestionType;
  explanation: string | null;
  options?: { id: string | null; text: string; correct: boolean }[];
  correctBoolean?: boolean | null;
  correctNumericValue?: string | null;
  absoluteTolerance?: string | null;
  unit?: NumericUnit | null;
  decimalPlaces?: number | null;
}

export type CreateQuestionRequest = QuestionPayload;

export interface PatchQuestionRequest extends Partial<QuestionPayload> {
  version: number;
}

/** `PUT /teacher/lessons/{lessonId}/questions/order`. */
export interface QuestionOrderRequest {
  questionIds: string[];
}

/** `POST /teacher/questions/{questionId}/duplicate` — pede a lição destino. */
export interface DuplicateQuestionRequest {
  targetLessonId: string;
}

/**
 * Retorno da exclusão.
 *
 * A API pode arquivar logicamente em vez de remover quando a questão já foi
 * respondida. A interface reflete o que a resposta indicar (Parte 5, §6.3).
 */
export interface QuestionDeletionResult {
  archived: boolean;
}
