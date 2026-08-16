import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type {
  CreateQuestionRequest,
  DuplicateQuestionRequest,
  PatchQuestionRequest,
  Question,
  QuestionDeletionResult,
  QuestionOrderRequest,
} from '../models/question';

/** Questões de uma lição — §7.2 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class QuestionService {
  private readonly api = inject(ApiClient);

  list(lessonId: string): Promise<Question[]> {
    return firstValueFrom(this.api.get<Question[]>(`/teacher/lessons/${lessonId}/questions`));
  }

  create(lessonId: string, body: CreateQuestionRequest): Promise<Question> {
    return firstValueFrom(this.api.post<Question>(`/teacher/lessons/${lessonId}/questions`, body));
  }

  update(questionId: string, body: PatchQuestionRequest): Promise<Question> {
    return firstValueFrom(this.api.patch<Question>(`/teacher/questions/${questionId}`, body));
  }

  duplicate(questionId: string, body: DuplicateQuestionRequest): Promise<Question> {
    return firstValueFrom(
      this.api.post<Question>(`/teacher/questions/${questionId}/duplicate`, body),
    );
  }

  /**
   * A API pode arquivar logicamente em vez de excluir quando a questão já foi
   * respondida. Um `204` sem corpo significa remoção efetiva.
   */
  async remove(questionId: string): Promise<QuestionDeletionResult> {
    const result = await firstValueFrom(
      this.api.delete<QuestionDeletionResult | null>(`/teacher/questions/${questionId}`),
    );
    return result ?? { archived: false };
  }

  /** Reordenação. A ordem otimista da tela é revertida se isto falhar. */
  reorder(lessonId: string, body: QuestionOrderRequest): Promise<Question[]> {
    return firstValueFrom(
      this.api.put<Question[]>(`/teacher/lessons/${lessonId}/questions/order`, body),
    );
  }
}
