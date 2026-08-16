import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type {
  AttemptHistoryItem,
  LessonDetail,
  LessonTrackItem,
} from '../models/lesson-track';

/**
 * Trilha e lição — §6.2 da spec de integração.
 *
 * A ordem, a disponibilidade e o motivo de bloqueio chegam prontos: este
 * service não filtra, não ordena e não decide nada.
 */
@Injectable({ providedIn: 'root' })
export class StudentLessonService {
  private readonly api = inject(ApiClient);

  /** Trilha ordenada da sala, com estado de bloqueio de cada item. */
  track(roomId: string): Promise<LessonTrackItem[]> {
    return firstValueFrom(
      this.api.get<LessonTrackItem[]>(`/student/rooms/${roomId}/lessons`),
    );
  }

  /** Teoria, materiais e situação do aluno na lição. */
  detail(roomId: string, lessonId: string): Promise<LessonDetail> {
    return firstValueFrom(
      this.api.get<LessonDetail>(`/student/rooms/${roomId}/lessons/${lessonId}`),
    );
  }

  /** Histórico de tentativas do aluno naquela lição. */
  attempts(roomId: string, lessonId: string): Promise<AttemptHistoryItem[]> {
    return firstValueFrom(
      this.api.get<AttemptHistoryItem[]>(
        `/student/rooms/${roomId}/lessons/${lessonId}/attempts`,
      ),
    );
  }
}
