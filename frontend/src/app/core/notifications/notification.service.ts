import { Injectable, inject } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';

import { ApiError } from '../api/problem-details';

export interface DestructiveConfirmOptions {
  header: string;
  message: string;
  acceptLabel?: string;
  rejectLabel?: string;
  /** `pi pi-...`. Padrão: triângulo de atenção. */
  icon?: string;
}

/**
 * Fachada de toasts e confirmações — Parte 1, §9.
 *
 * As features não dependem da API do PrimeNG diretamente; dependem disto.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);

  success(summary: string, detail?: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 4000 });
  }

  info(summary: string, detail?: string): void {
    this.messages.add({ severity: 'info', summary, detail, life: 5000 });
  }

  warn(summary: string, detail?: string): void {
    this.messages.add({ severity: 'warn', summary, detail, life: 6000 });
  }

  /**
   * Exibe uma falha. Aceita `ApiError` para aproveitar a mensagem já traduzida
   * por status e o `traceId`, útil no suporte.
   */
  error(error: ApiError | string, summary = 'Não foi possível concluir'): void {
    if (typeof error === 'string') {
      this.messages.add({ severity: 'error', summary, detail: error, life: 8000 });
      return;
    }

    this.messages.add({
      severity: error.isOffline ? 'warn' : 'error',
      summary: error.isOffline ? 'Sem conexão' : summary,
      detail: error.detail,
      life: 8000,
    });
  }

  /**
   * Confirmação obrigatória para arquivar, remover aluno, regenerar código,
   * excluir e desativar conta — §9 da spec de integração.
   */
  destructive(options: DestructiveConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      this.confirmation.confirm({
        header: options.header,
        message: options.message,
        icon: options.icon ?? 'pi pi-exclamation-triangle',
        acceptLabel: options.acceptLabel ?? 'Confirmar',
        rejectLabel: options.rejectLabel ?? 'Cancelar',
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-text',
        accept: () => resolve(true),
        reject: () => resolve(false),
      });
    });
  }

  /** Confirmação neutra, para ações reversíveis. */
  confirm(options: DestructiveConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      this.confirmation.confirm({
        header: options.header,
        message: options.message,
        icon: options.icon ?? 'pi pi-question-circle',
        acceptLabel: options.acceptLabel ?? 'Confirmar',
        rejectLabel: options.rejectLabel ?? 'Cancelar',
        rejectButtonStyleClass: 'p-button-text',
        accept: () => resolve(true),
        reject: () => resolve(false),
      });
    });
  }
}
