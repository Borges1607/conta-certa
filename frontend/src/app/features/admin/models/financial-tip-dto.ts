/** Dicas financeiras — §8.3 da spec de integração. */

/**
 * `publicationDate` é `LocalDate` (`YYYY-MM-DD`), **não** instante.
 *
 * É o único campo de data da aplicação que não sofre conversão de fuso. Ele
 * entra e sai como texto e só é convertido para `Date` dentro do seletor, por
 * `parseLocalDate`/`toLocalDateString` — ver `util/local-date.ts` e a
 * Parte 6, §6.
 */
export interface FinancialTip {
  id: string;
  title: string;
  /** Markdown. Renderizado exclusivamente por `cc-markdown`, sanitizado. */
  content: string;
  sourceUrl: string | null;
  publicationDate: string;
  active: boolean;
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateFinancialTipRequest {
  title: string;
  content: string;
  sourceUrl?: string | null;
  /** `YYYY-MM-DD`, sem hora e sem fuso. */
  publicationDate: string;
  active: boolean;
}

/** `version` obrigatório — conflito recarrega, nunca sobrescreve. */
export interface PatchFinancialTipRequest {
  title?: string;
  content?: string;
  sourceUrl?: string | null;
  publicationDate?: string;
  active?: boolean;
  version: number;
}

export interface FinancialTipFilters {
  search?: string;
  active?: boolean;
}

/**
 * Resultado de `DELETE /admin/financial-tips/{tipId}`.
 *
 * A API pode remover de fato (`204`) ou arquivar logicamente, devolvendo a
 * dica com `active: false`. A interface reflete o que voltou (Parte 6, §6).
 */
export type DeleteTipOutcome =
  | { kind: 'deleted' }
  | { kind: 'archived'; tip: FinancialTip };
