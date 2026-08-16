import type { AttemptStatus, NumericUnit, QuestionType } from '../../../core/models/enums';

/**
 * Tentativa **em andamento** — Parte 4, §6.1.
 *
 * Este arquivo é a metade "sem gabarito" do contrato de tentativas, e existe
 * separado de `attempt-result.ts` de propósito. Nenhum tipo daqui tem
 * `correct`, `correctAnswer` ou `explanation`; o componente da tentativa em
 * andamento importa **somente** deste arquivo, e por isso é o compilador — não
 * a disciplina de quem escreve a tela — que garante o critério "nenhum gabarito
 * aparece antes de a tentativa terminar" (§11 da spec de integração).
 *
 * Ao mexer aqui: se você sentir vontade de acrescentar um campo de correção,
 * ele pertence a `attempt-result.ts`.
 */

/** Alternativa apresentada ao aluno. Repare que não existe `correct`. */
export interface AttemptOption {
  id: string;
  text: string;
}

/** Regras de apresentação de uma questão numérica — §7.2 da spec. */
export interface AttemptNumericSpec {
  unit: NumericUnit;
  decimalPlaces: number;
}

/** Questão sorteada para a tentativa. */
export interface AttemptQuestion {
  questionSnapshotId: string;
  type: QuestionType;
  prompt: string;
  order: number;
  /** Presente em `SINGLE_CHOICE` e `MULTIPLE_CHOICE`. */
  options?: AttemptOption[];
  /** Presente em `NUMERIC`. */
  numeric?: AttemptNumericSpec;
}

/**
 * Corpo enviado em `PUT /student/attempts/{id}/answers/{questionSnapshotId}`
 * — §6.3 da spec de integração.
 *
 * `numericValue` é **string decimal**, nunca `float` (§2.1 da spec).
 */
export interface AnswerPayload {
  selectedOptionIds?: string[];
  booleanValue?: boolean;
  numericValue?: string;
}

/**
 * Resposta já registrada, como a tela da tentativa a conhece.
 *
 * A API devolve `correct` ao registrar, mas o `AttemptService` descarta esse
 * campo antes de o valor chegar aqui: durante a tentativa a interface só sabe
 * que a questão foi **respondida**, jamais se acertou (Parte 4, §6.1 e §6.3).
 */
export interface RecordedAnswer {
  questionSnapshotId: string;
  answeredAt: string;
  /** O que o próprio aluno respondeu, para reexibir em modo somente leitura. */
  answer?: AnswerPayload;
}

/** `GET /student/attempts/{attemptId}` — hidratação da tela, inclusive em recarga. */
export interface AttemptDetail {
  attemptId: string;
  assignmentId: string;
  roomId: string;
  lessonId: string;
  lessonTitle: string;
  status: AttemptStatus;
  startedAt: string;
  /** `null` quando a atribuição não tem tempo limite: nenhum cronômetro é exibido. */
  expiresAt: string | null;
  timeLimitMinutes: number | null;
  questions: AttemptQuestion[];
  answers: RecordedAnswer[];
  passingScorePercent: number;
}

/** `POST /student/room-lessons/{assignmentId}/attempts` — inicia ou devolve a ativa. */
export interface AttemptStartResponse {
  attemptId: string;
  status: AttemptStatus;
  startedAt: string;
  expiresAt: string | null;
}
