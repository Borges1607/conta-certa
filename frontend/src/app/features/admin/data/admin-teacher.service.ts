import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type {
  AdminTeacher,
  CreateTeacherRequest,
  PatchTeacherRequest,
  TeacherFilters,
} from '../models/teacher-dto';

/**
 * Professores — §8.1 da spec de integração.
 *
 * Não há e nunca haverá um método que defina a senha de um professor: a criação
 * dispara convite e o próprio professor escolhe a senha (Parte 6, §9).
 */
@Injectable({ providedIn: 'root' })
export class AdminTeacherService {
  private readonly api = inject(ApiClient);

  private readonly base = '/admin/teachers';

  list(filters: TeacherFilters, query: PageQuery): Promise<Page<AdminTeacher>> {
    return firstValueFrom(
      this.api.getPage<AdminTeacher>(this.base, query, {
        params: {
          search: filters.search,
          status: filters.status,
          institutionId: filters.institutionId,
        },
      }),
    );
  }

  get(teacherId: string): Promise<AdminTeacher> {
    return firstValueFrom(this.api.get<AdminTeacher>(`${this.base}/${teacherId}`));
  }

  /** Cria em `PENDING` e envia o convite. */
  create(body: CreateTeacherRequest): Promise<AdminTeacher> {
    return firstValueFrom(this.api.post<AdminTeacher>(this.base, body));
  }

  update(teacherId: string, body: PatchTeacherRequest): Promise<AdminTeacher> {
    return firstValueFrom(this.api.patch<AdminTeacher>(`${this.base}/${teacherId}`, body));
  }

  activate(teacherId: string): Promise<AdminTeacher> {
    return firstValueFrom(this.api.post<AdminTeacher>(`${this.base}/${teacherId}/activate`));
  }

  /** Revoga **todas** as sessões do professor, com efeito imediato. */
  deactivate(teacherId: string): Promise<AdminTeacher> {
    return firstValueFrom(this.api.post<AdminTeacher>(`${this.base}/${teacherId}/deactivate`));
  }

  /**
   * Envia link de redefinição. O admin não vê nem escolhe a senha — só dispara
   * o e-mail, e o retorno é neutro por princípio.
   */
  sendPasswordReset(teacherId: string): Promise<void> {
    return firstValueFrom(this.api.post<void>(`${this.base}/${teacherId}/password-reset`));
  }
}
