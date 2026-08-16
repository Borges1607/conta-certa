import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { firstValueFrom } from 'rxjs';

import { ApiClient, type UploadEvent } from '../../../core/api/api-client';
import type { ContentStatus } from '../../../core/models/enums';
import type { Page, PageQuery } from '../../../core/models/page';
import type {
  CreateLessonRequest,
  LessonDetail,
  LessonImage,
  LessonSummary,
  PatchLessonRequest,
} from '../models/lesson';

/** Acervo de lições — §7.2 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class LessonService {
  private readonly api = inject(ApiClient);
  private readonly base = '/teacher/lessons';

  list(
    query: PageQuery = {},
    status: ContentStatus | null = null,
    search = '',
  ): Promise<Page<LessonSummary>> {
    return firstValueFrom(
      this.api.getPage<LessonSummary>(this.base, query, { params: { status, search } }),
    );
  }

  /** Acervo publicado, para o seletor da trilha da sala. */
  async publishedOptions(search = ''): Promise<LessonSummary[]> {
    const page = await this.list({ page: 0, size: 100, sort: 'title,asc' }, 'PUBLISHED', search);
    return page.content;
  }

  get(lessonId: string): Promise<LessonDetail> {
    return firstValueFrom(this.api.get<LessonDetail>(`${this.base}/${lessonId}`));
  }

  create(body: CreateLessonRequest): Promise<LessonDetail> {
    return firstValueFrom(this.api.post<LessonDetail>(this.base, body));
  }

  update(lessonId: string, body: PatchLessonRequest): Promise<LessonDetail> {
    return firstValueFrom(this.api.patch<LessonDetail>(`${this.base}/${lessonId}`, body));
  }

  /** `422` quando faltam questões — o motivo do servidor é exibido. */
  publish(lessonId: string): Promise<LessonDetail> {
    return firstValueFrom(this.api.post<LessonDetail>(`${this.base}/${lessonId}/publish`));
  }

  archive(lessonId: string): Promise<LessonDetail> {
    return firstValueFrom(this.api.post<LessonDetail>(`${this.base}/${lessonId}/archive`));
  }

  /** Copia a lição **e** as questões. */
  duplicate(lessonId: string): Promise<LessonDetail> {
    return firstValueFrom(this.api.post<LessonDetail>(`${this.base}/${lessonId}/duplicate`));
  }

  /**
   * Imagem do Markdown: PNG/JPEG/WebP até 5 MB, com progresso real.
   * O componente decide o que fazer com `413` e `415`.
   */
  uploadImage(lessonId: string, file: File): Observable<UploadEvent<LessonImage>> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.api.postMultipart<LessonImage>(`${this.base}/${lessonId}/images`, form);
  }
}
