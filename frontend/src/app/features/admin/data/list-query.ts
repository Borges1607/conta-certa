import { Signal, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, convertToParamMap } from '@angular/router';

import { DEFAULT_PAGE_SIZE } from '../../../core/models/page';
import type { PageQuery } from '../../../core/models/page';

/**
 * Filtros de lista na query string — Parte 6, §3.
 *
 * Toda lista do admin guarda busca, filtros, página e ordenação na URL. É o que
 * torna o link compartilhável, o que faz o filtro sobreviver a recarga e o que
 * permite os cartões do dashboard apontarem para uma lista já filtrada.
 *
 * A URL é a **única** fonte de verdade do filtro: nenhuma tela mantém uma cópia
 * em signal próprio, senão as duas divergem no botão "voltar".
 */
export interface ListQueryHandle {
  readonly params: Signal<ParamMap>;
  /** `page`, `size` e `sort` já normalizados para o `ApiClient`. */
  readonly pageQuery: Signal<PageQuery>;

  text(name: string): Signal<string>;
  option<T extends string>(name: string, allowed: readonly T[]): Signal<T | undefined>;
  flag(name: string): Signal<boolean | undefined>;

  /** Altera filtros e volta para a primeira página. */
  setFilters(patch: Record<string, string | null>, replaceUrl?: boolean): void;
  /** Altera apenas paginação/ordenação, preservando os filtros. */
  setPage(query: PageQuery): void;
}

export function createListQuery(): ListQueryHandle {
  const route = inject(ActivatedRoute);
  const router = inject(Router);

  const params = toSignal(route.queryParamMap, {
    initialValue: convertToParamMap(route.snapshot.queryParams),
  });

  const navigate = (queryParams: Record<string, string | null>, replaceUrl: boolean): void => {
    void router.navigate([], {
      relativeTo: route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  };

  return {
    params,

    pageQuery: computed<PageQuery>(() => {
      const map = params();
      const page = Number(map.get('page') ?? 0);
      const size = Number(map.get('size') ?? DEFAULT_PAGE_SIZE);
      return {
        page: Number.isFinite(page) && page > 0 ? page : 0,
        size: Number.isFinite(size) && size > 0 ? size : DEFAULT_PAGE_SIZE,
        sort: map.get('sort') ?? undefined,
      };
    }),

    text: (name) => computed(() => params().get(name) ?? ''),

    option: <T extends string>(name: string, allowed: readonly T[]) =>
      computed(() => {
        const value = params().get(name);
        return value !== null && (allowed as readonly string[]).includes(value)
          ? (value as T)
          : undefined;
      }),

    flag: (name) =>
      computed(() => {
        const value = params().get(name);
        if (value === 'true') {
          return true;
        }
        return value === 'false' ? false : undefined;
      }),

    setFilters(patch, replaceUrl = false) {
      // Trocar de filtro sem zerar a página levaria a uma lista vazia sempre que
      // o novo recorte tivesse menos páginas que o anterior.
      navigate({ ...patch, page: null }, replaceUrl);
    },

    setPage(query) {
      navigate(
        {
          page: query.page && query.page > 0 ? String(query.page) : null,
          size: query.size && query.size !== DEFAULT_PAGE_SIZE ? String(query.size) : null,
          sort: query.sort ?? null,
        },
        false,
      );
    },
  };
}
