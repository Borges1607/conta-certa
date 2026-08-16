import { FormBuilder } from '@angular/forms';

import { ApiError } from '../../core/api/problem-details';
import {
  SERVER_ERROR_KEY,
  applyFieldErrors,
  autoClearServerErrors,
  clearServerError,
  firstInvalidField,
} from './apply-field-errors';

/**
 * Protege dois critérios da spec de integração: "formulários exibem
 * `fieldErrors` junto aos campos correspondentes" (§11) e "preservação de
 * formulário quando houver erro de validação" (§9).
 */
describe('applyFieldErrors', () => {
  const fb = new FormBuilder();

  const buildForm = () =>
    fb.nonNullable.group({
      name: ['Instituto Exemplo'],
      cnpj: ['12345678000190'],
      contactEmail: ['contato@example.com'],
    });

  const validationError = (fieldErrors: { field: string; message: string }[]) =>
    new ApiError({
      status: 422,
      code: 'VALIDATION_ERROR',
      detail: 'One or more fields are invalid.',
      fieldErrors,
    });

  it('marca os controles correspondentes', () => {
    const form = buildForm();
    const { applied, orphans } = applyFieldErrors(
      form,
      validationError([
        { field: 'name', message: 'must not be blank' },
        { field: 'cnpj', message: 'invalid CNPJ' },
      ]),
    );

    expect(applied.length).toBe(2);
    expect(orphans).toEqual([]);
    expect(form.controls.name.errors?.[SERVER_ERROR_KEY]).toBe('must not be blank');
    expect(form.controls.cnpj.errors?.[SERVER_ERROR_KEY]).toBe('invalid CNPJ');
    expect(form.controls.name.touched).toBe(true);
  });

  it('preserva os valores digitados', () => {
    const form = buildForm();
    form.patchValue({ name: 'Rascunho do usuário' });

    applyFieldErrors(form, validationError([{ field: 'name', message: 'já existe' }]));

    // O que o usuário digitou continua lá para ele corrigir.
    expect(form.controls.name.value).toBe('Rascunho do usuário');
    expect(form.controls.cnpj.value).toBe('12345678000190');
    expect(form.controls.contactEmail.value).toBe('contato@example.com');
  });

  it('coleta erros sem campo correspondente em vez de descartá-los', () => {
    const form = buildForm();
    const { applied, orphans } = applyFieldErrors(
      form,
      validationError([{ field: 'campoQueNaoExiste', message: 'algo errado' }]),
    );

    expect(applied).toEqual([]);
    expect(orphans.length).toBe(1);
    expect(orphans[0].message).toBe('algo errado');
  });

  it('resolve caminho aninhado pelo último segmento', () => {
    const form = buildForm();
    applyFieldErrors(form, validationError([{ field: 'institution.name', message: 'inválido' }]));
    expect(form.controls.name.errors?.[SERVER_ERROR_KEY]).toBe('inválido');
  });

  it('preserva erros locais já existentes no controle', () => {
    const form = buildForm();
    form.controls.name.setErrors({ required: true });

    applyFieldErrors(form, validationError([{ field: 'name', message: 'do servidor' }]));

    expect(form.controls.name.errors?.['required']).toBe(true);
    expect(form.controls.name.errors?.[SERVER_ERROR_KEY]).toBe('do servidor');
  });

  it('clearServerError remove só o erro de servidor', () => {
    const form = buildForm();
    form.controls.name.setErrors({ required: true, [SERVER_ERROR_KEY]: 'x' });

    clearServerError(form.controls.name);

    expect(form.controls.name.errors?.['required']).toBe(true);
    expect(form.controls.name.errors?.[SERVER_ERROR_KEY]).toBeUndefined();
  });

  it('autoClearServerErrors limpa quando o usuário corrige o campo', () => {
    const form = buildForm();
    autoClearServerErrors(form);

    applyFieldErrors(form, validationError([{ field: 'name', message: 'já existe' }]));
    expect(form.controls.name.errors?.[SERVER_ERROR_KEY]).toBe('já existe');

    form.controls.name.setValue('Outro nome');

    // O erro se referia ao valor antigo; manter seria mentira.
    expect(form.controls.name.errors).toBeNull();
  });

  it('aceita a lista de fieldErrors diretamente', () => {
    const form = buildForm();
    applyFieldErrors(form, [{ field: 'cnpj', message: 'duplicado' }]);
    expect(form.controls.cnpj.errors?.[SERVER_ERROR_KEY]).toBe('duplicado');
  });

  it('firstInvalidField aponta o campo a receber foco', () => {
    const form = buildForm();
    applyFieldErrors(form, validationError([{ field: 'cnpj', message: 'inválido' }]));
    expect(firstInvalidField(form)).toBe('cnpj');
  });
});
