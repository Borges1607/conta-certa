import { Signal, computed, signal, untracked } from '@angular/core';

import { ApiError } from '../../core/api/problem-details';

/**
 * Estado de uma tela que consulta dados — Parte 2, §6.
 *
 * A distinção entre `loading` e `refreshing` é obrigatória: `loading` é a
 * primeira carga e mostra esqueleto; `refreshing` é atualização em segundo
 * plano e **mantém os dados na tela**. Nunca substituir conteúdo já visível por
 * esqueleto (§9 da spec de integração).
 */
export type PageState<T> =
  | { kind: 'loading' }
  | { kind: 'ready'; data: T; refreshing: boolean }
  | { kind: 'error'; error: ApiError };

export interface PageStateHandle<T> {
  readonly state: Signal<PageState<T>>;
  readonly data: Signal<T | null>;
  readonly isLoading: Signal<boolean>;
  readonly isRefreshing: Signal<boolean>;
  readonly error: Signal<ApiError | null>;

  /** Primeira carga: mostra esqueleto. */
  load(): Promise<void>;
  /** Recarrega mantendo o conteúdo visível. */
  refresh(): Promise<void>;
  /** Nova tentativa após erro: volta ao esqueleto. */
  retry(): Promise<void>;
  /** Atualiza os dados localmente, sem ir à API. */
  patch(update: (current: T) => T): void;
  set(data: T): void;
}

/**
 * Cria o ciclo de carga de uma página a partir de um carregador.
 *
 * ```ts
 * private readonly rooms = createPageState(() => this.service.list());
 * ```
 */
export function createPageState<T>(loader: () => Promise<T>): PageStateHandle<T> {
  const state = signal<PageState<T>>({ kind: 'loading' });

  const run = async (mode: 'initial' | 'background'): Promise<void> => {
    // `untracked`: as telas chamam `load()` de dentro de um `effect` que observa
    // a query string. Ler `state` no contexto reativo faria o effect depender do
    // próprio signal que ele escreve na linha seguinte — loop infinito que trava
    // a aba antes mesmo de a requisição sair.
    const current = untracked(state);

    if (mode === 'background' && current.kind === 'ready') {
      state.set({ ...current, refreshing: true });
    } else {
      state.set({ kind: 'loading' });
    }

    try {
      state.set({ kind: 'ready', data: await loader(), refreshing: false });
    } catch (error) {
      if (mode === 'background' && current.kind === 'ready') {
        // Falha em atualização de segundo plano não apaga o que está na tela.
        state.set({ ...current, refreshing: false });
        throw error;
      }
      state.set({
        kind: 'error',
        error: error instanceof ApiError ? error : unexpectedError(error),
      });
    }
  };

  return {
    state: state.asReadonly(),
    data: computed(() => {
      const s = state();
      return s.kind === 'ready' ? s.data : null;
    }),
    isLoading: computed(() => state().kind === 'loading'),
    isRefreshing: computed(() => {
      const s = state();
      return s.kind === 'ready' && s.refreshing;
    }),
    error: computed(() => {
      const s = state();
      return s.kind === 'error' ? s.error : null;
    }),

    load: () => run('initial'),
    refresh: () => run('background'),
    retry: () => run('initial'),

    patch(update) {
      const s = state();
      if (s.kind === 'ready') {
        state.set({ ...s, data: update(s.data) });
      }
    },
    set(data) {
      state.set({ kind: 'ready', data, refreshing: false });
    },
  };
}

function unexpectedError(error: unknown): ApiError {
  return new ApiError({
    status: 0,
    code: 'UNEXPECTED',
    detail: error instanceof Error ? error.message : 'Algo deu errado. Tente novamente.',
  });
}
