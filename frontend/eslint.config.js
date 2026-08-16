// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

/**
 * Regras arquiteturais do Conta Certa.
 *
 * Boa parte das varreduras da Parte 7, §3 das specs vive aqui: são regras que
 * protegem critérios da spec de integração que testes unitários cobrem mal.
 */
module.exports = defineConfig([
  {
    ignores: ['dist/**', 'node_modules/**', '.angular/**'],
  },

  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'cc', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'cc', style: 'kebab-case' },
      ],
      '@angular-eslint/prefer-on-push-component-change-detection': 'error',
      '@angular-eslint/use-lifecycle-interface': 'error',

      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-definitions': ['error', 'interface'],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],

      // Sanitização e confiança — visão geral §3.7 e Parte 2, §4.3.
      'no-restricted-syntax': [
        'error',
        {
          // Só a **escrita** é perigosa. Ler innerHTML é como um teste verifica
          // o que foi renderizado, e proibir isso empurraria os testes para
          // asserções piores.
          selector: "AssignmentExpression > MemberExpression[property.name='innerHTML']",
          message:
            'Escrever innerHTML só é permitido dentro de cc-markdown, depois da sanitização. Ver Parte 2, §4.3.',
        },
        {
          selector: 'MemberExpression[property.name=/^bypassSecurityTrust/]',
          message:
            'bypassSecurityTrust* é proibido no projeto. O conteúdo deve passar por DOMPurify em cc-markdown.',
        },
      ],
    },
  },

  // O HttpClient é encapsulado pelo ApiClient — nenhuma feature o injeta.
  {
    files: ['src/app/**/*.ts'],
    ignores: ['src/app/core/api/**', 'src/app/core/interceptors/**', 'src/mocks/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: '@angular/common/http',
              importNames: ['HttpClient'],
              message: 'Use o ApiClient de core/api. Ver Parte 1, §3.',
            },
          ],
        },
      ],
    },
  },

  // Persistência de sessão é exclusiva de core/auth. A preferência de tema não
  // é sessão, e é a única outra exceção.
  {
    files: ['src/app/**/*.ts'],
    ignores: [
      'src/app/core/auth/**',
      'src/app/shared/layout/theme.service.ts',
      'src/mocks/**',
      '**/*.spec.ts',
    ],
    rules: {
      'no-restricted-globals': [
        'error',
        {
          name: 'localStorage',
          message: 'Persistência de sessão pertence a core/auth/token-storage.ts. Ver Parte 1, §4.1.',
        },
        {
          name: 'sessionStorage',
          message: 'Persistência de sessão pertence a core/auth/token-storage.ts. Ver Parte 1, §4.1.',
        },
      ],
    },
  },

  // core não conhece shared nem features; features não se conhecem entre si.
  {
    files: ['src/app/core/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/shared/**', '**/features/**'],
              message: 'core não pode importar de shared nem de features. Ver visão geral, §4.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/app/shared/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/features/**'],
              message: 'shared não pode importar de features. Ver visão geral, §4.',
            },
          ],
        },
      ],
    },
  },

  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {
      '@angular-eslint/template/prefer-control-flow': 'error',
    },
  },
]);