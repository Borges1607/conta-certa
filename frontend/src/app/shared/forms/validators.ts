import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '../../core/models/auth';

/**
 * Validadores locais.
 *
 * São **conveniência**: dão retorno imediato ao usuário. O backend sempre
 * valida de novo, e é a resposta dele que manda (§4.1 da spec de integração).
 */

/**
 * Regra de senha da §4.1: 8 a 72 caracteres, ao menos uma letra e um número.
 */
export const passwordRuleValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = control.value;
  if (typeof value !== 'string' || value === '') {
    return null;
  }

  const valid =
    value.length >= PASSWORD_MIN_LENGTH &&
    value.length <= PASSWORD_MAX_LENGTH &&
    /[a-zA-Z]/.test(value) &&
    /[0-9]/.test(value);

  return valid ? null : { passwordRule: true };
};

/** Confirmação de senha. Aplicado no grupo, reporta no campo de confirmação. */
export function passwordMatchValidator(
  passwordField: string,
  confirmField: string,
): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordField)?.value;
    const confirm = group.get(confirmField);

    if (!confirm || confirm.value === '' || password === confirm.value) {
      removeError(confirm, 'passwordMatch');
      return null;
    }

    confirm.setErrors({ ...(confirm.errors ?? {}), passwordMatch: true });
    return { passwordMatch: true };
  };
}

/** CNPJ com 14 dígitos e dígitos verificadores válidos. */
export const cnpjValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const digits = onlyDigits(control.value);
  if (digits === '') {
    return null;
  }
  return isValidCnpj(digits) ? null : { pattern: true };
};

export function onlyDigits(value: unknown): string {
  return typeof value === 'string' ? value.replace(/\D/g, '') : '';
}

/** `12345678000190` → `12.345.678/0001-90`. */
export function formatCnpj(value: string): string {
  const digits = onlyDigits(value);
  if (digits.length !== 14) {
    return value;
  }
  return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`;
}

export function isValidCnpj(digits: string): boolean {
  if (digits.length !== 14 || /^(\d)\1{13}$/.test(digits)) {
    return false;
  }

  const check = (length: number): number => {
    let sum = 0;
    let weight = length - 7;
    for (let i = 0; i < length; i++) {
      sum += Number(digits[i]) * weight;
      weight = weight === 2 ? 9 : weight - 1;
    }
    const remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  };

  return check(12) === Number(digits[12]) && check(13) === Number(digits[13]);
}

function removeError(control: AbstractControl | null, key: string): void {
  if (!control?.errors?.[key]) {
    return;
  }
  const { [key]: _removed, ...rest } = control.errors;
  control.setErrors(Object.keys(rest).length > 0 ? rest : null);
}
