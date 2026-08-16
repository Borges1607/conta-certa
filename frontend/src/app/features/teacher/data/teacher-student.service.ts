import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type { ExtraAttemptsRequest, ExtraAttemptsResult, RoomStudent } from '../models/student';

/** Alunos de uma sala — §7.1 e §7.3 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class TeacherStudentService {
  private readonly api = inject(ApiClient);

  list(roomId: string, query: PageQuery = {}, search = ''): Promise<Page<RoomStudent>> {
    return firstValueFrom(
      this.api.getPage<RoomStudent>(`/teacher/rooms/${roomId}/students`, query, {
        params: { search },
      }),
    );
  }

  /**
   * Remove a matrícula. O histórico é preservado pela API e o aluno pode
   * reingressar com o código, recuperando tudo (Parte 5, §5).
   */
  remove(roomId: string, studentId: string): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`/teacher/rooms/${roomId}/students/${studentId}`));
  }

  /** Tentativa extra numa atribuição específica — §7.3 da spec. */
  grantExtraAttempts(
    assignmentId: string,
    studentId: string,
    body: ExtraAttemptsRequest,
  ): Promise<ExtraAttemptsResult> {
    return firstValueFrom(
      this.api.post<ExtraAttemptsResult>(
        `/teacher/room-lessons/${assignmentId}/students/${studentId}/extra-attempts`,
        body,
      ),
    );
  }
}
