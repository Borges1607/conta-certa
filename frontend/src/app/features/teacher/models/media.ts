import type { ContentStatus, MaterialKind, MediaViewType } from '../../../core/models/enums';
import type { Page } from '../../../core/models/page';

/** Videoaula do acervo do professor — §7.4 da spec de integração. */
export interface TeacherVideo {
  id: string;
  title: string;
  description: string | null;
  category: string | null;
  /** Link externo. É validado e pré-visualizado antes de salvar. */
  url: string;
  status: ContentStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateVideoRequest {
  title: string;
  description: string | null;
  category: string | null;
  url: string;
}

export interface PatchVideoRequest {
  version: number;
  title?: string;
  description?: string | null;
  category?: string | null;
  url?: string;
}

/** Arquivo já enviado por `POST /teacher/materials/files`. */
export interface MaterialFile {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
}

/** Material do acervo — link externo ou arquivo. */
export interface TeacherMaterial {
  id: string;
  title: string;
  description: string | null;
  category: string | null;
  kind: MaterialKind;
  /** Preenchido quando `kind` é `EXTERNAL_LINK`. */
  url: string | null;
  /** Preenchido quando `kind` é `FILE`. */
  file: MaterialFile | null;
  status: ContentStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateMaterialRequest {
  title: string;
  description: string | null;
  category: string | null;
  kind: MaterialKind;
  url: string | null;
  fileId: string | null;
}

export interface PatchMaterialRequest {
  version: number;
  title?: string;
  description?: string | null;
  category?: string | null;
  kind?: MaterialKind;
  url?: string | null;
  fileId?: string | null;
}

/** Publicação de uma mídia numa sala — §7.4 da spec. */
export interface MediaAssignment {
  id: string;
  roomId: string;
  mediaType: MediaViewType;
  mediaId: string;
  title: string;
  /** Nulo quando a mídia fica solta na sala, sem vínculo com lição. */
  lessonAssignmentId: string | null;
  lessonTitle: string | null;
  position: number;
  createdAt: string;
  version: number;
}

export interface CreateMediaAssignmentRequest {
  mediaType: MediaViewType;
  mediaId: string;
  lessonAssignmentId: string | null;
}

export interface PatchMediaAssignmentRequest {
  version: number;
  lessonAssignmentId?: string | null;
  position?: number;
}

/** Uma linha de `GET /teacher/media/{mediaType}/{mediaId}/views`. */
export interface MediaView {
  studentId: string;
  fullName: string;
  registrationNumber: string | null;
  firstViewedAt: string;
  lastViewedAt: string;
}

/** Página de visualizações, acrescida do total de alunos que abriram. */
export interface MediaViewsPage extends Page<MediaView> {
  totalViewers: number;
}
