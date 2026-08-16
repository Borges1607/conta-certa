import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type {
  CreateFinancialTipRequest,
  DeleteTipOutcome,
  FinancialTip,
  FinancialTipFilters,
  PatchFinancialTipRequest,
} from '../models/financial-tip-dto';

/** Dicas financeiras — §8.3 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class FinancialTipService {
  private readonly api = inject(ApiClient);

  private readonly base = '/admin/financial-tips';

  list(filters: FinancialTipFilters, query: PageQuery): Promise<Page<FinancialTip>> {
    return firstValueFrom(
      this.api.getPage<FinancialTip>(this.base, query, {
        params: { search: filters.search, active: filters.active },
      }),
    );
  }

  get(tipId: string): Promise<FinancialTip> {
    return firstValueFrom(this.api.get<FinancialTip>(`${this.base}/${tipId}`));
  }

  /** `publicationDate` sai exatamente como `YYYY-MM-DD` — sem hora, sem fuso. */
  create(body: CreateFinancialTipRequest): Promise<FinancialTip> {
    return firstValueFrom(this.api.post<FinancialTip>(this.base, body));
  }

  update(tipId: string, body: PatchFinancialTipRequest): Promise<FinancialTip> {
    return firstValueFrom(this.api.patch<FinancialTip>(`${this.base}/${tipId}`, body));
  }

  activate(tipId: string): Promise<FinancialTip> {
    return firstValueFrom(this.api.post<FinancialTip>(`${this.base}/${tipId}/activate`));
  }

  deactivate(tipId: string): Promise<FinancialTip> {
    return firstValueFrom(this.api.post<FinancialTip>(`${this.base}/${tipId}/deactivate`));
  }

  /**
   * A API pode excluir de fato (`204`, corpo vazio) ou arquivar logicamente,
   * devolvendo a dica. Quem decide é ela; a tela apenas reflete o retorno
   * (Parte 6, §6).
   */
  async remove(tipId: string): Promise<DeleteTipOutcome> {
    const body = await firstValueFrom(
      this.api.delete<FinancialTip | null>(`${this.base}/${tipId}`),
    );
    return body ? { kind: 'archived', tip: body } : { kind: 'deleted' };
  }
}
