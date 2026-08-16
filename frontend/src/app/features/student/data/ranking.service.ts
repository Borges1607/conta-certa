import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient, pageParams } from '../../../core/api/api-client';
import type { PageQuery } from '../../../core/models/page';
import type { StudentRankingPage } from '../models/ranking';

/**
 * Ranking da sala — §6.4 da spec de integração.
 *
 * Sempre relativo a uma sala. Os nomes já chegam anonimizados pela API; o
 * frontend não transforma nada (Parte 4, §8).
 */
@Injectable({ providedIn: 'root' })
export class RankingService {
  private readonly api = inject(ApiClient);

  ranking(roomId: string, query: PageQuery = {}): Promise<StudentRankingPage> {
    return firstValueFrom(
      this.api.get<StudentRankingPage>(`/student/rooms/${roomId}/ranking`, {
        params: pageParams(query),
      }),
    );
  }
}
