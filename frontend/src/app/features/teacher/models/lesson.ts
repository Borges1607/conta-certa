import type { ContentStatus } from '../../../core/models/enums';

/**
 * Acervo de lições — §7.2 da spec de integração.
 *
 * A lição pertence ao professor, não a uma sala: é reutilizável entre salas
 * (Parte 5, §6.1).
 */
export interface LessonSummary {
  id: string;
  title: string;
  summary: string | null;
  status: ContentStatus;
  questionCount: number;
  /** Quantas salas usam esta lição hoje. */
  assignmentCount: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface LessonDetail extends LessonSummary {
  theoryMarkdown: string;
}

export interface CreateLessonRequest {
  title: string;
  summary: string | null;
  theoryMarkdown: string;
}

export interface PatchLessonRequest {
  version: number;
  title?: string;
  summary?: string | null;
  theoryMarkdown?: string;
}

/** Retorno de `POST /teacher/lessons/{lessonId}/images`. */
export interface LessonImage {
  id: string;
  /** URL já pronta para referenciar no Markdown. */
  url: string;
  fileName: string;
}

export interface LessonFilters {
  status: ContentStatus | null;
  search: string;
}
