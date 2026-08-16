import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type {
  CreateRoomRequest,
  PatchRoomRequest,
  TeacherRoomDetail,
  TeacherRoomSummary,
} from '../models/room';

/**
 * Salas do professor — §7.1 da spec de integração.
 *
 * Todas as rotas da área ficam aqui: nenhum literal de caminho de API aparece
 * em componente (visão geral, §7).
 */
@Injectable({ providedIn: 'root' })
export class TeacherRoomService {
  private readonly api = inject(ApiClient);
  private readonly base = '/teacher/rooms';

  list(query: PageQuery = {}, search = '', archived: boolean | null = null): Promise<Page<TeacherRoomSummary>> {
    return firstValueFrom(
      this.api.getPage<TeacherRoomSummary>(this.base, query, {
        params: { search, archived },
      }),
    );
  }

  /** Lista enxuta para seletores (filtro de relatório, por exemplo). */
  async options(): Promise<TeacherRoomSummary[]> {
    const page = await this.list({ page: 0, size: 100 });
    return page.content;
  }

  get(roomId: string): Promise<TeacherRoomDetail> {
    return firstValueFrom(this.api.get<TeacherRoomDetail>(`${this.base}/${roomId}`));
  }

  create(body: CreateRoomRequest): Promise<TeacherRoomDetail> {
    return firstValueFrom(this.api.post<TeacherRoomDetail>(this.base, body));
  }

  /** `version` é obrigatório no corpo — conflito vira `409 VERSION_CONFLICT`. */
  update(roomId: string, body: PatchRoomRequest): Promise<TeacherRoomDetail> {
    return firstValueFrom(this.api.patch<TeacherRoomDetail>(`${this.base}/${roomId}`, body));
  }

  archive(roomId: string): Promise<TeacherRoomDetail> {
    return firstValueFrom(this.api.post<TeacherRoomDetail>(`${this.base}/${roomId}/archive`));
  }

  /** Só permitido em sala nunca utilizada; a API responde `409` caso contrário. */
  remove(roomId: string): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`${this.base}/${roomId}`));
  }

  /** Copia a configuração. Alunos e progresso **não** são copiados. */
  duplicate(roomId: string): Promise<TeacherRoomDetail> {
    return firstValueFrom(this.api.post<TeacherRoomDetail>(`${this.base}/${roomId}/duplicate`));
  }

  regenerateCode(roomId: string): Promise<TeacherRoomDetail> {
    return firstValueFrom(
      this.api.post<TeacherRoomDetail>(`${this.base}/${roomId}/regenerate-code`),
    );
  }
}
