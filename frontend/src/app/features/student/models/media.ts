import type { MaterialKind } from '../../../core/models/enums';

/**
 * Videoaulas e materiais publicados na sala — §6.4 da spec de integração.
 *
 * Só chega ao aluno conteúdo publicado; a filtragem é da API.
 */

/** Vínculo opcional com uma lição, usado para agrupar as listas (Parte 4, §7). */
export interface MediaLessonLink {
  lessonId: string;
  lessonTitle: string;
}

export interface StudentVideo {
  id: string;
  title: string;
  description: string | null;
  /** Link externo; a incorporação é feita por `cc-video-embed`. */
  url: string;
  durationMinutes: number | null;
  lesson: MediaLessonLink | null;
  viewed: boolean;
  firstViewedAt: string | null;
}

export interface StudentMaterial {
  id: string;
  title: string;
  description: string | null;
  kind: MaterialKind;
  /** Presente quando `kind` é `EXTERNAL_LINK`. */
  externalUrl: string | null;
  /** Presente quando `kind` é `FILE`; acessado por `GET /files/{fileId}/download`. */
  fileId: string | null;
  fileName: string | null;
  fileSizeBytes: number | null;
  contentType: string | null;
  lesson: MediaLessonLink | null;
  viewed: boolean;
  firstViewedAt: string | null;
}

/**
 * Lista de mídias com o progresso de consumo.
 *
 * `viewedCount` e `totalCount` vêm da API — a tela não conta nada por conta
 * própria (Parte 4, §7).
 */
export interface MediaCollection<T> {
  items: T[];
  viewedCount: number;
  totalCount: number;
}

/** Agrupamento por lição, montado na tela a partir de `lesson`. */
export interface MediaGroup<T> {
  key: string;
  title: string;
  items: T[];
}
