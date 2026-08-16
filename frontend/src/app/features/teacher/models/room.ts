import type { Grade } from '../../../core/models/enums';
import type { InstitutionSummary } from '../../../core/models/institution';

/**
 * Salas na visão do professor — §7.1 da spec de integração.
 *
 * A instituição **nunca** é campo de formulário: a API a deriva do professor
 * autenticado (Parte 5, §4 e §12).
 */
export interface TeacherRoomSummary {
  id: string;
  name: string;
  description: string | null;
  grade: Grade;
  contentTopics: string[];
  /** Código de ingresso exibido em destaque, com botão de copiar. */
  joinCode: string;
  passingScorePercent: number;
  archived: boolean;
  studentCount: number;
  lessonCount: number;
  createdAt: string;
  updatedAt: string;
  /** Reenviado em toda alteração — §3 da spec de integração. */
  version: number;
}

export interface TeacherRoomDetail extends TeacherRoomSummary {
  institution: InstitutionSummary;
  teacher: { id: string; fullName: string };
  /**
   * A sala nunca foi usada e por isso pode ser excluída. Quem decide é a API;
   * o frontend só deixa de oferecer a ação quando ela diz que não dá.
   */
  deletable: boolean;
}

export interface CreateRoomRequest {
  name: string;
  description: string | null;
  grade: Grade;
  contentTopics: string[];
  passingScorePercent: number;
}

/** `version` é obrigatório: sem ele não há detecção de conflito. */
export interface PatchRoomRequest {
  version: number;
  name?: string;
  description?: string | null;
  grade?: Grade;
  contentTopics?: string[];
  passingScorePercent?: number;
}

/** Nota mínima padrão da criação — Parte 5, §4. */
export const DEFAULT_PASSING_SCORE_PERCENT = 50;
