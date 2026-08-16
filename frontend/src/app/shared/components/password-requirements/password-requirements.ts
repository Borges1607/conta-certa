import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '../../../core/models/auth';

interface Requirement {
  label: string;
  met: boolean;
}

/**
 * Régua de senha — Parte 3, §4.
 *
 * Vive em `shared` porque é usada tanto pela jornada pública (cadastro,
 * redefinição, aceite de convite) quanto pela troca de senha da conta, e
 * `features/x` não pode importar de `features/y` (visão geral, §4).
 *
 * A §4.1 da spec de integração permite exibir a regra, mas lembra que "o
 * backend sempre a valida". Isto é retorno visual, não autoridade.
 */
@Component({
  selector: 'cc-password-requirements',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="requirements" aria-label="Requisitos da senha">
      @for (item of requirements(); track item.label) {
        <li [class.is-met]="item.met">
          <i
            class="pi"
            [class.pi-check-circle]="item.met"
            [class.pi-circle]="!item.met"
            aria-hidden="true"
          ></i>
          <span>{{ item.label }}</span>
          <span class="cc-sr-only">{{ item.met ? '(atendido)' : '(pendente)' }}</span>
        </li>
      }
    </ul>
  `,
  styleUrl: './password-requirements.scss',
})
export class PasswordRequirementsComponent {
  readonly password = input<string>('');

  protected readonly requirements = computed<Requirement[]>(() => {
    const value = this.password() ?? '';
    return [
      {
        label: `De ${PASSWORD_MIN_LENGTH} a ${PASSWORD_MAX_LENGTH} caracteres`,
        met: value.length >= PASSWORD_MIN_LENGTH && value.length <= PASSWORD_MAX_LENGTH,
      },
      { label: 'Ao menos uma letra', met: /[a-zA-Z]/.test(value) },
      { label: 'Ao menos um número', met: /[0-9]/.test(value) },
    ];
  });
}
