import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { MediaViewType } from '../../../core/models/enums';
import type { PageQuery } from '../../../core/models/page';
import { pageParams } from '../../../core/api/api-client';
import type {
  CreateMediaAssignmentRequest,
  MediaAssignment,
  MediaViewsPage,
  PatchMediaAssignmentRequest,
} from '../models/media';

/** Publicação de mídias em sala e visualizações — §7.4 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class MediaAssignmentService {
  private readonly api = inject(ApiClient);

  private path(roomId: string): string {
    return `/teacher/rooms/${roomId}/media-assignments`;
  }

  list(roomId: string): Promise<MediaAssignment[]> {
    return firstValueFrom(this.api.get<MediaAssignment[]>(this.path(roomId)));
  }

  create(roomId: string, body: CreateMediaAssignmentRequest): Promise<MediaAssignment> {
    return firstValueFrom(this.api.post<MediaAssignment>(this.path(roomId), body));
  }

  update(
    roomId: string,
    assignmentId: string,
    body: PatchMediaAssignmentRequest,
  ): Promise<MediaAssignment> {
    return firstValueFrom(
      this.api.patch<MediaAssignment>(`${this.path(roomId)}/${assignmentId}`, body),
    );
  }

  remove(roomId: string, assignmentId: string): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`${this.path(roomId)}/${assignmentId}`));
  }

  /**
   * Visualizações por aluno, com `firstViewedAt` e `lastViewedAt`.
   *
   * A resposta é paginada e traz também o total de alunos que abriram, por isso
   * não usa `getPage`: o corpo tem um campo a mais que `Page<T>`.
   */
  views(mediaType: MediaViewType, mediaId: string, query: PageQuery = {}): Promise<MediaViewsPage> {
    return firstValueFrom(
      this.api.get<MediaViewsPage>(`/teacher/media/${mediaType}/${mediaId}/views`, {
        params: pageParams(query),
      }),
    );
  }
}
