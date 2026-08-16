import { FormBuilder, FormControl } from '@angular/forms';

import {
  cnpjValidator,
  formatCnpj,
  isValidCnpj,
  onlyDigits,
  passwordMatchValidator,
  passwordRuleValidator,
} from './validators';

describe('validators', () => {
  describe('passwordRuleValidator', () => {
    const check = (value: string) => passwordRuleValidator(new FormControl(value));

    it('aceita senha com letra e número dentro do tamanho', () => {
      expect(check('abc12345')).toBeNull();
      expect(check('Senha2026')).toBeNull();
    });

    it('recusa senha só com letras ou só com números', () => {
      // §4.1 da spec: ao menos uma letra e um número.
      expect(check('abcdefgh')).toEqual({ passwordRule: true });
      expect(check('12345678')).toEqual({ passwordRule: true });
    });

    it('recusa senha curta demais', () => {
      expect(check('abc1234')).toEqual({ passwordRule: true });
    });

    it('recusa senha acima de 72 caracteres', () => {
      expect(check('a1'.repeat(40))).toEqual({ passwordRule: true });
    });

    it('não valida campo vazio — isso é papel do required', () => {
      expect(check('')).toBeNull();
    });
  });

  describe('passwordMatchValidator', () => {
    const fb = new FormBuilder();
    const build = () =>
      fb.nonNullable.group(
        { password: [''], passwordConfirm: [''] },
        { validators: passwordMatchValidator('password', 'passwordConfirm') },
      );

    it('reporta o erro no campo de confirmação', () => {
      const form = build();
      form.patchValue({ password: 'abc12345', passwordConfirm: 'abc99999' });

      expect(form.controls.passwordConfirm.errors?.['passwordMatch']).toBe(true);
      expect(form.invalid).toBe(true);
    });

    it('limpa o erro quando as senhas passam a coincidir', () => {
      const form = build();
      form.patchValue({ password: 'abc12345', passwordConfirm: 'abc99999' });
      form.patchValue({ passwordConfirm: 'abc12345' });

      expect(form.controls.passwordConfirm.errors).toBeNull();
      expect(form.valid).toBe(true);
    });

    it('não reclama enquanto a confirmação está vazia', () => {
      const form = build();
      form.patchValue({ password: 'abc12345' });
      expect(form.controls.passwordConfirm.errors?.['passwordMatch']).toBeUndefined();
    });
  });

  describe('CNPJ', () => {
    it('valida dígitos verificadores', () => {
      expect(isValidCnpj('11222333000181')).toBe(true);
      expect(isValidCnpj('11222333000182')).toBe(false);
    });

    it('recusa sequências repetidas e tamanho errado', () => {
      expect(isValidCnpj('11111111111111')).toBe(false);
      expect(isValidCnpj('112223330001')).toBe(false);
    });

    it('normaliza para 14 dígitos sem pontuação', () => {
      // O que vai para a API é sempre isto — §8.2 da spec.
      expect(onlyDigits('11.222.333/0001-81')).toBe('11222333000181');
    });

    it('formata para exibição', () => {
      expect(formatCnpj('11222333000181')).toBe('11.222.333/0001-81');
    });

    it('o validador aceita valor com máscara', () => {
      expect(cnpjValidator(new FormControl('11.222.333/0001-81'))).toBeNull();
      expect(cnpjValidator(new FormControl('11.222.333/0001-82'))).toEqual({ pattern: true });
    });

    it('não valida campo vazio', () => {
      expect(cnpjValidator(new FormControl(''))).toBeNull();
    });
  });
});
