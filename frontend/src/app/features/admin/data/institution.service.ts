import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type { InstitutionOption } from '../../../core/models/institution';
import { normalizeCnpj } from '../util/cnpj';
import type {
  AdminInstitution,
  CreateInstitutionRequest,
  InstitutionFilters,
  PatchInstitutionRequest,
} from '../models/institution-dto';

/**
 * Instituições — §8.2 da spec de integração.
 *
 * O service é a última barreira antes do contrato: mesmo que uma tela erre e
 * mande CNPJ com pontuação, aqui ele sai com 14 dígitos (Parte 6, §4).
 */
@Injectable({ providedIn: 'root' })
export class InstitutionService {
  private readonly api = inject(ApiClient);

  private readonly base = '/admin/institutions';
  /** Endpoint público reutilizado pelo formulário de professor — §5.2. */
  private readonly optionsPath = '/institutions/options';

  list(filters: InstitutionFilters, query: PageQuery): Promise<Page<AdminInstitution>> {
    return firstValueFrom(
      this.api.getPage<AdminInstitution>(this.base, query, {
        params: { search: filters.search, active: filters.active },
      }),
    );
  }

  get(institutionId: string): Promise<AdminInstitution> {
    return firstValueFrom(this.api.get<AdminInstitution>(`${this.base}/${institutionId}`));
  }

  create(body: CreateInstitutionRequest): Promise<AdminInstitution> {
    return firstValueFrom(this.api.post<AdminInstitution>(this.base, normalize(body)));
  }

  /** `version` faz parte do corpo e é obrigatório pelo tipo. */
  update(institutionId: string, body: PatchInstitutionRequest): Promise<AdminInstitution> {
    return firstValueFrom(
      this.api.patch<AdminInstitution>(`${this.base}/${institutionId}`, normalize(body)),
    );
  }

  activate(institutionId: string): Promise<AdminInstitution> {
    return firstValueFrom(
      this.api.post<AdminInstitution>(`${this.base}/${institutionId}/activate`),
    );
  }

  /** Bloqueia **novos** vínculos; os existentes continuam — Parte 6, §4. */
  deactivate(institutionId: string): Promise<AdminInstitution> {
    return firstValueFrom(
      this.api.post<AdminInstitution>(`${this.base}/${institutionId}/deactivate`),
    );
  }

  /** Só instituições sem histórico. Com vínculos a API responde `409`. */
  remove(institutionId: string): Promise<void> {
    return firstValueFrom(this.api.delete<void>(`${this.base}/${institutionId}`));
  }

  /** Opções ativas para o formulário de professor. */
  options(): Promise<InstitutionOption[]> {
    return firstValueFrom(
      this.api.get<InstitutionOption[]>(this.optionsPath, { params: { active: true } }),
    );
  }
}

/** Único ponto que decide o formato do CNPJ que sai daqui. */
function normalize<T extends { cnpj?: string }>(body: T): T {
  return body.cnpj === undefined ? body : { ...body, cnpj: normalizeCnpj(body.cnpj) };
}
