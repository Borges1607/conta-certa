import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * CNPJ — Parte 6, §4.
 *
 * Regra que separa apresentação de contrato: a **máscara existe apenas na
 * tela**; o que trafega para a API são sempre 14 dígitos sem pontuação.
 *
 * A validação de dígito verificador é cortesia local para evitar uma ida ao
 * servidor por engano de digitação. Quem decide é o servidor: um CNPJ que passe
 * aqui e seja recusado lá vira erro de campo normalmente.
 */

export const CNPJ_LENGTH = 14;

export function onlyDigits(value: string | null | undefined): string {
  return (value ?? '').replace(/\D/g, '');
}

/** Qualquer entrada → até 14 dígitos, sem pontuação. É isto que vai para a API. */
export function normalizeCnpj(value: string | null | undefined): string {
  return onlyDigits(value).slice(0, CNPJ_LENGTH);
}

/** Dígitos → `12.345.678/0001-90`. Formata parcialmente durante a digitação. */
export function formatCnpj(value: string | null | undefined): string {
  const digits = normalizeCnpj(value);
  if (digits.length === 0) {
    return '';
  }

  let out = digits.slice(0, 2);
  if (digits.length > 2) {
    out += `.${digits.slice(2, 5)}`;
  }
  if (digits.length > 5) {
    out += `.${digits.slice(5, 8)}`;
  }
  if (digits.length > 8) {
    out += `/${digits.slice(8, 12)}`;
  }
  if (digits.length > 12) {
    out += `-${digits.slice(12, 14)}`;
  }
  return out;
}

/** Dígitos verificadores. Rejeita também os repetidos, que passam no cálculo. */
export function isValidCnpj(value: string | null | undefined): boolean {
  const digits = normalizeCnpj(value);
  if (digits.length !== CNPJ_LENGTH || /^(\d)\1{13}$/.test(digits)) {
    return false;
  }

  const numbers = digits.split('').map(Number);
  const firstWeights = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
  const secondWeights = [6, ...firstWeights];

  return (
    checkDigit(numbers.slice(0, 12), firstWeights) === numbers[12] &&
    checkDigit(numbers.slice(0, 13), secondWeights) === numbers[13]
  );
}

function checkDigit(numbers: readonly number[], weights: readonly number[]): number {
  const sum = numbers.reduce((acc, n, i) => acc + n * weights[i], 0);
  const rest = sum % 11;
  return rest < 2 ? 0 : 11 - rest;
}

/** Validador local do formulário. O controle guarda o valor já normalizado. */
export const cnpjValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const raw = typeof control.value === 'string' ? control.value : '';
  if (raw === '') {
    return null;
  }
  return isValidCnpj(raw) ? null : { cnpj: true };
};
