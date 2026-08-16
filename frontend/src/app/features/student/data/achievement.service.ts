import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { StudentAchievement } from '../models/achievement';

/**
 * Conquistas da sala — §6.4 da spec de integração.
 *
 * Cada conquista é calculada por sala pela API. O frontend não verifica
 * critério nenhum: só recebe `unlocked` e apresenta.
 */
@Injectable({ providedIn: 'root' })
export class AchievementService {
  private readonly api = inject(ApiClient);

  achievements(roomId: string): Promise<StudentAchievement[]> {
    return firstValueFrom(
      this.api.get<StudentAchievement[]>(`/student/rooms/${roomId}/achievements`),
    );
  }
}
