import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

import { SERVER_ERROR_KEY } from '../../forms/apply-field-errors';

/** Mensagens dos validadores locais. */
const VALIDATION_MESSAGES: Record<string, (error: unknown) => string> = {
  required: () => 'Campo obrigatório.',
  email: () => 'Informe um e-mail válido.',
  minlength: (e) => `Mínimo de ${(e as { requiredLength: number }).requiredLength} caracteres.`,
  maxlength: (e) => `Máximo de ${(e as { requiredLength: number }).requiredLength} caracteres.`,
  min: (e) => `Valor mínimo: ${(e as { min: number }).min}.`,
  max: (e) => `Valor máximo: ${(e as { max: number }).max}.`,
  pattern: () => 'Formato inválido.',
  passwordRule: () => 'A senha precisa ter ao menos uma letra e um número.',
  passwordMatch: () => 'As senhas não coincidem.',
};

/**
 * Rótulo, controle e mensagem de erro — Parte 2, §4.2.
 *
 * O erro vindo do servidor tem prioridade sobre os validadores locais: ele é
 * mais específico e é o que a API de fato recusou.
 */
@Component({
  selector: 'cc-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field" [class.field--invalid]="showError()">
      <label class="field__label" [attr.for]="inputId()">
        {{ label() }}
        @if (required()) {
          <span class="field__required" aria-hidden="true">*</span>
        }
      </label>

      <ng-content />

      @if (showError()) {
        <small class="field__error" role="alert">{{ errorMessage() }}</small>
      } @else if (hint()) {
        <small class="field__hint">{{ hint() }}</small>
      }
    </div>
  `,
  styleUrl: './form-field.scss',
})
export class FormFieldComponent {
  readonly label = input.required<string>();
  readonly inputId = input<string>('');
  readonly control = input<AbstractControl | null>(null);
  readonly hint = input('');
  readonly required = input(false);

  protected showError(): boolean {
    const control = this.control();
    if (!control) {
      return false;
    }
    // Erro de servidor aparece imediatamente; erro local só depois de o
    // usuário interagir, para o formulário não nascer vermelho.
    const hasServerError = Boolean(control.errors?.[SERVER_ERROR_KEY]);
    return hasServerError || (control.invalid && (control.touched || control.dirty));
  }

  protected errorMessage(): string {
    const errors = this.control()?.errors;
    if (!errors) {
      return '';
    }

    const serverError = errors[SERVER_ERROR_KEY];
    if (typeof serverError === 'string') {
      return serverError;
    }

    for (const [key, value] of Object.entries(errors)) {
      const message = VALIDATION_MESSAGES[key];
      if (message) {
        return message(value);
      }
    }

    return 'Valor inválido.';
  }
}
