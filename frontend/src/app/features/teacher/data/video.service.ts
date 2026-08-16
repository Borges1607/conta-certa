import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type { CreateVideoRequest, PatchVideoRequest, TeacherVideo } from '../models/media';

/** Videoaulas do acervo próprio — §7.4 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class VideoService {
  private readonly api = inject(ApiClient);
  private readonly base = '/teacher/videos';

  list(query: PageQuery = {}, search = '', category: string | null = null): Promise<Page<TeacherVideo>> {
    return firstValueFrom(
      this.api.getPage<TeacherVideo>(this.base, query, { params: { search, category } }),
    );
  }

  async options(): Promise<TeacherVideo[]> {
    const page = await this.list({ page: 0, size: 100, sort: 'title,asc' });
    return page.content;
  }

  get(videoId: string): Promise<TeacherVideo> {
    return firstValueFrom(this.api.get<TeacherVideo>(`${this.base}/${videoId}`));
  }

  create(body: CreateVideoRequest): Promise<TeacherVideo> {
    return firstValueFrom(this.api.post<TeacherVideo>(this.base, body));
  }

  update(videoId: string, body: PatchVideoRequest): Promise<TeacherVideo> {
    return firstValueFrom(this.api.patch<TeacherVideo>(`${this.base}/${videoId}`, body));
  }

  /** A API arquiva quando o vídeo já foi publicado em alguma sala. */
  remove(videoId: string): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`${this.base}/${videoId}`));
  }
}
