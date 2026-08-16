import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient, type QueryParams } from '../../../core/api/api-client';
import type { Page, PageQuery } from '../../../core/models/page';
import type {
  ReportAttemptRow,
  ReportFilters,
  ReportOverview,
  ReportRankingRow,
  ReportStudentRow,
} from '../models/report';

/**
 * Relatórios — §7.5 da spec de integração.
 *
 * O CSV é **gerado no backend**: o frontend só baixa o blob autorizado. Nenhum
 * número é recalculado aqui (§11 da spec).
 */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly api = inject(ApiClient);
  private readonly base = '/teacher/reports';

  overview(filters: ReportFilters): Promise<ReportOverview> {
    return firstValueFrom(
      this.api.get<ReportOverview>(`${this.base}/overview`, { params: toParams(filters) }),
    );
  }

  students(filters: ReportFilters, query: PageQuery = {}): Promise<Page<ReportStudentRow>> {
    return firstValueFrom(
      this.api.getPage<ReportStudentRow>(`${this.base}/students`, query, {
        params: toParams(filters),
      }),
    );
  }

  studentAttempts(
    studentId: string,
    filters: ReportFilters,
    query: PageQuery = {},
  ): Promise<Page<ReportAttemptRow>> {
    return firstValueFrom(
      this.api.getPage<ReportAttemptRow>(`${this.base}/students/${studentId}/attempts`, query, {
        params: toParams(filters),
      }),
    );
  }

  ranking(filters: ReportFilters, query: PageQuery = {}): Promise<Page<ReportRankingRow>> {
    return firstValueFrom(
      this.api.getPage<ReportRankingRow>(`${this.base}/ranking`, query, {
        params: toParams(filters),
      }),
    );
  }

  /** CSV pronto do backend. O frontend não monta CSV (Parte 5, §9). */
  exportCsv(filters: ReportFilters): Promise<Blob> {
    return firstValueFrom(
      this.api.download(`${this.base}/export.csv`, { params: toParams(filters) }),
    );
  }
}

/**
 * Serializa os filtros.
 *
 * `period=ALL` remove `from` e `to`; qualquer outro período envia a janela
 * explícita. `buildParams` do `ApiClient` descarta os nulos.
 */
export function toParams(filters: ReportFilters): QueryParams {
  const allTime = filters.period === 'ALL';
  return {
    roomId: filters.roomId,
    lessonId: filters.lessonId,
    period: allTime ? 'ALL' : null,
    from: allTime ? null : filters.from,
    to: allTime ? null : filters.to,
  };
}
