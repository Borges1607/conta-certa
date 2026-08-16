import { Signal, signal } from '@angular/core';

/**
 * Proteção contra duplo envio — Parte 1, §10.
 *
 * A §9 da spec de integração exige bloqueio contra duplo envio em toda mutação.
 * Em vez de cada tela inventar o seu, toda mutação passa por aqui e o botão
 * correspondente liga `[loading]` e `[disabled]` ao mesmo signal.
 */
export interface SubmitGuard {
  readonly submitting: Signal<boolean>;
  /**
   * Executa `fn` se nada estiver em andamento. Chamadas concorrentes são
   * ignoradas e resolvem para `undefined`.
   */
  run<T>(fn: () => Promise<T>): Promise<T | undefined>;
}

export function createSubmitGuard(): SubmitGuard {
  const submitting = signal(false);

  return {
    submitting: submitting.asReadonly(),
    async run<T>(fn: () => Promise<T>): Promise<T | undefined> {
      if (submitting()) {
        return undefined;
      }
      submitting.set(true);
      try {
        return await fn();
      } finally {
        submitting.set(false);
      }
    },
  };
}
