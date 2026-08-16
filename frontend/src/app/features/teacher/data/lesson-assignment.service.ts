import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type {
  AssignmentOrderRequest,
  CreateAssignmentRequest,
  LessonAssignment,
  PatchAssignmentRequest,
} from '../models/assignment';

/** Trilha da sala — §7.3 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class LessonAssignmentService {
  private readonly api = inject(ApiClient);

  private path(roomId: string): string {
    return `/teacher/rooms/${roomId}/lesson-assignments`;
  }

  list(roomId: string): Promise<LessonAssignment[]> {
    return firstValueFrom(this.api.get<LessonAssignment[]>(this.path(roomId)));
  }

  create(roomId: string, body: CreateAssignmentRequest): Promise<LessonAssignment> {
    return firstValueFrom(this.api.post<LessonAssignment>(this.path(roomId), body));
  }

  update(
    roomId: string,
    assignmentId: string,
    body: PatchAssignmentRequest,
  ): Promise<LessonAssignment> {
    return firstValueFrom(
      this.api.patch<LessonAssignment>(`${this.path(roomId)}/${assignmentId}`, body),
    );
  }

  /** Só atribuição futura sai da trilha; a API responde `409` caso contrário. */
  remove(roomId: string, assignmentId: string): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`${this.path(roomId)}/${assignmentId}`));
  }

  reorder(roomId: string, body: AssignmentOrderRequest): Promise<LessonAssignment[]> {
    return firstValueFrom(this.api.put<LessonAssignment[]>(`${this.path(roomId)}/order`, body));
  }
}
