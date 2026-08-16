import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import { ApiClient, type UploadEvent } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type {
  CreateMaterialRequest,
  MaterialFile,
  PatchMaterialRequest,
  TeacherMaterial,
} from '../models/media';

/** Materiais do acervo próprio — §7.4 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class MaterialService {
  private readonly api = inject(ApiClient);
  private readonly base = '/teacher/materials';

  list(
    query: PageQuery = {},
    search = '',
    kind: string | null = null,
  ): Promise<Page<TeacherMaterial>> {
    return firstValueFrom(
      this.api.getPage<TeacherMaterial>(this.base, query, { params: { search, kind } }),
    );
  }

  async options(): Promise<TeacherMaterial[]> {
    const page = await this.list({ page: 0, size: 100, sort: 'title,asc' });
    return page.content;
  }

  get(materialId: string): Promise<TeacherMaterial> {
    return firstValueFrom(this.api.get<TeacherMaterial>(`${this.base}/${materialId}`));
  }

  create(body: CreateMaterialRequest): Promise<TeacherMaterial> {
    return firstValueFrom(this.api.post<TeacherMaterial>(this.base, body));
  }

  update(materialId: string, body: PatchMaterialRequest): Promise<TeacherMaterial> {
    return firstValueFrom(this.api.patch<TeacherMaterial>(`${this.base}/${materialId}`, body));
  }

  remove(materialId: string): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`${this.base}/${materialId}`));
  }

  /**
   * Upload multipart de PDF/PPT/PPTX até 10 MB, com progresso real.
   * `413` e `415` são tratados na tela, mantendo o arquivo selecionado.
   */
  uploadFile(file: File): Observable<UploadEvent<MaterialFile>> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.api.postMultipart<MaterialFile>(`${this.base}/files`, form);
  }
}
