/** Paginação — §2.3 da spec de integração. */

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type SortDirection = 'asc' | 'desc';

export interface PageQuery {
  /** Padrão 0. */
  page?: number;
  /** Padrão 20, máximo 100. */
  size?: number;
  /** `field,asc` ou `field,desc`. */
  sort?: string;
}

export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;

export function buildSort(field: string, direction: SortDirection): string {
  return `${field},${direction}`;
}

/** Página vazia, útil como estado inicial sem tornar o tipo anulável. */
export function emptyPage<T>(size = DEFAULT_PAGE_SIZE): Page<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
}