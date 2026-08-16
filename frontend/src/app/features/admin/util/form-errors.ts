import type { FormGroup } from '@angular/forms';

import { ApiError } from '../../../core/api/problem-details';
import type { NotificationService } from '../../../core/notifications/notification.service';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';

export interface FormErrorOptions {
  /**
   * Chamado em `409 VERSION_CONFLICT`. A tela deve mostrar o aviso com
   * "Recarregar" — nunca salvar por cima.
   */
  onVersionConflict?: () => void;
  /**
   * Campo que recebe um `409` de duplicidade (CNPJ ou e-mail já cadastrado).
   * Sem isto, o usuário veria um toast genérico e não saberia qual campo
   * corrigir.
   */
  duplicateField?: { name: string; message: string };
}

/**
 * Erro de submissão de formulário — §9 e §11 da spec de integração.
 *
 * Concentra as três respostas que a interface precisa dar, na ordem em que a
 * spec as distingue: conflito de versão, erro de campo e falha geral. O
 * formulário **nunca** é resetado — `applyFieldErrors` só anexa os erros.
 */
export function handleFormError(
  error: unknown,
  form: FormGroup,
  notification: NotificationService,
  options: FormErrorOptions = {},
): void {
  if (!(error instanceof ApiError)) {
    notification.error('Algo deu errado. Tente novamente.');
    return;
  }

  if (error.isVersionConflict) {
    options.onVersionConflict?.();
    return;
  }

  if (error.fieldErrors.length > 0) {
    const { orphans } = applyFieldErrors(form, error);
    if (orphans.length > 0) {
      notification.error(orphans.map((o) => o.message).join(' '), 'Verifique os dados');
    }
    return;
  }

  if (error.status === 409 && options.duplicateField) {
    applyFieldErrors(form, [
      { field: options.duplicateField.name, message: options.duplicateField.message },
    ]);
    return;
  }

  notification.error(error);
}

/**
 * Toast de falha para operações fora de formulário (ativar, desativar,
 * recarregar). Preserva a mensagem já traduzida do `ApiError`.
 */
export function notifyError(
  error: unknown,
  notification: NotificationService,
  summary?: string,
): void {
  if (error instanceof ApiError) {
    notification.error(error, summary);
    return;
  }
  notification.error('Algo deu errado. Tente novamente.', summary);
}
