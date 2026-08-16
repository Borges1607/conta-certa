import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import { silentContext } from '../../../core/api/http-context';
import type { MediaViewType } from '../../../core/models/enums';
import type { MediaCollection, StudentMaterial, StudentVideo } from '../models/media';

/** Videoaulas, materiais e registro de visualização — §6.4 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class StudentMediaService {
  private readonly api = inject(ApiClient);

  videos(roomId: string): Promise<MediaCollection<StudentVideo>> {
    return firstValueFrom(
      this.api.get<MediaCollection<StudentVideo>>(`/student/rooms/${roomId}/videos`),
    );
  }

  materials(roomId: string): Promise<MediaCollection<StudentMaterial>> {
    return firstValueFrom(
      this.api.get<MediaCollection<StudentMaterial>>(`/student/rooms/${roomId}/materials`),
    );
  }

  /**
   * Registra a abertura de uma mídia.
   *
   * O registro é idempotente por aluno e mídia, **não bloqueia a abertura** e
   * sua falha não impede o consumo do conteúdo (Parte 4, §7). Por isso o erro
   * é engolido aqui: quem chama não tem o que fazer com ele, e transformar um
   * problema de telemetria em toast de erro atrapalharia o aluno.
   *
   * Vai em contexto silencioso para não acender a barra de progresso global.
   */
  async registerView(mediaType: MediaViewType, mediaId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.api.post<unknown>(
          `/student/media/${mediaType}/${mediaId}/view`,
          {},
          { context: silentContext() },
        ),
      );
    } catch {
      // Silêncio proposital — ver comentário acima.
    }
  }
}
