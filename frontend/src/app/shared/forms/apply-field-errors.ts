import { AbstractControl, FormGroup } from '@angular/forms';

import { ApiError, type FieldError } from '../../core/api/problem-details';

/** Chave usada para o erro vindo do servidor. */
export const SERVER_ERROR_KEY = 'server';

export interface AppliedFieldErrors {
  /** Erros que encontraram um controle correspondente. */
  applied: FieldError[];
  /** Erros sem controle no formulário — precisam de um resumo no topo. */
  orphans: FieldError[];
}

/**
 * Casa os `fieldErrors` da API com os controles do formulário — Parte 2, §4.2.
 *
 * Cumpre dois critérios da spec de integração de uma vez: "formulários exibem
 * `fieldErrors` junto aos campos correspondentes" e "preservação de formulário
 * quando houver erro de validação".
 *
 * **O formulário não é resetado.** Os valores digitados permanecem; só os erros
 * são anexados.
 */
export function applyFieldErrors(form: FormGroup, error: ApiError | readonly FieldError[]): AppliedFieldErrors {
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : error;
  const applied: FieldError[] = [];
  const orphans: FieldError[] = [];

  for (const fieldError of fieldErrors) {
    const control = findControl(form, fieldError.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), [SERVER_ERROR_KEY]: fieldError.message });
      control.markAsTouched();
      applied.push(fieldError);
    } else {
      // Nada é silenciosamente perdido: o que não tem campo vira resumo.
      orphans.push(fieldError);
    }
  }

  return { applied, orphans };
}

/**
 * Remove o erro de servidor de um controle. Deve ser chamado quando o usuário
 * altera o campo — o erro se refere ao valor antigo.
 */
export function clearServerError(control: AbstractControl): void {
  if (!control.errors?.[SERVER_ERROR_KEY]) {
    return;
  }

  const { [SERVER_ERROR_KEY]: _removed, ...rest } = control.errors;
  control.setErrors(Object.keys(rest).length > 0 ? rest : null);
}

/**
 * Liga a limpeza automática: qualquer alteração do usuário apaga o erro de
 * servidor daquele campo.
 */
export function autoClearServerErrors(form: FormGroup): void {
  for (const control of Object.values(form.controls)) {
    control.valueChanges.subscribe(() => clearServerError(control));
  }
}

/** Nome do primeiro campo inválido, para receber foco. */
export function firstInvalidField(form: FormGroup): string | null {
  for (const [name, control] of Object.entries(form.controls)) {
    if (control.invalid) {
      return name;
    }
  }
  return null;
}

/** Marca tudo como tocado, para os erros locais aparecerem na submissão. */
export function markAllTouched(form: FormGroup): void {
  form.markAllAsTouched();
}

/**
 * A API usa caminhos como `institution.name`; o formulário pode ter esse
 * controle aninhado ou achatado. Tentamos as duas formas.
 */
function findControl(form: FormGroup, field: string): AbstractControl | null {
  return form.get(field) ?? form.get(field.split('.').pop() ?? field);
}
