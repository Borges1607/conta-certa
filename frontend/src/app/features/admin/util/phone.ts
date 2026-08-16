import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { onlyDigits } from './cnpj';

/**
 * Telefone — Parte 6, §4.
 *
 * A API recebe E.164 (`+5548999999999`). A máscara `(48) 99999-9999` é auxílio
 * de digitação e nunca chega ao contrato.
 */

const BR_COUNTRY_CODE = '55';

/** Máscara → `+55DDDNÚMERO`. Assume Brasil quando não vier código de país. */
export function toE164(value: string | null | undefined): string {
  const digits = onlyDigits(value);
  if (digits.length === 0) {
    return '';
  }
  const national = digits.startsWith(BR_COUNTRY_CODE) && digits.length > 11 ? digits.slice(2) : digits;
  return `+${BR_COUNTRY_CODE}${national.slice(0, 11)}`;
}

/** Só a parte nacional (DDD + número), que é o que a máscara desenha. */
export function nationalDigits(value: string | null | undefined): string {
  const digits = onlyDigits(value);
  const national = digits.startsWith(BR_COUNTRY_CODE) && digits.length > 11 ? digits.slice(2) : digits;
  return national.slice(0, 11);
}

/** `+5548999999999` → `(48) 99999-9999`. Formata parcialmente ao digitar. */
export function formatPhone(value: string | null | undefined): string {
  const digits = nationalDigits(value);
  if (digits.length === 0) {
    return '';
  }
  if (digits.length <= 2) {
    return `(${digits}`;
  }

  const area = digits.slice(0, 2);
  const rest = digits.slice(2);
  // Celular tem 9 dígitos, fixo tem 8: o ponto do hífen muda conforme o total.
  const split = rest.length > 8 ? 5 : 4;

  return rest.length <= split
    ? `(${area}) ${rest}`
    : `(${area}) ${rest.slice(0, split)}-${rest.slice(split)}`;
}

/** Aceita fixo (10 dígitos) e celular (11). */
export const phoneValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const raw = typeof control.value === 'string' ? control.value : '';
  if (raw === '') {
    return null;
  }
  const digits = nationalDigits(raw);
  return digits.length === 10 || digits.length === 11 ? null : { phone: true };
};
