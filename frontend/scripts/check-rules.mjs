#!/usr/bin/env node
/**
 * Varreduras arquiteturais — Parte 7, §3 das specs de frontend.
 *
 * Protegem critérios da §11 da spec de integração que testes unitários cobrem
 * mal, porque tratam de código que **não deve existir** em vez de
 * comportamento que deve funcionar. O ESLint cobre o que dá para expressar
 * como regra de sintaxe; o resto está aqui.
 *
 * Uma violação legítima se marca com o comentário `cc-allow: <id-da-regra>`
 * na mesma linha ou na linha imediatamente acima — deliberadamente visível na
 * revisão, do mesmo jeito que um `eslint-disable`.
 */

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..');
const SRC = join(ROOT, 'src');

/**
 * @typedef {{
 *   id: string,
 *   description: string,
 *   pattern: RegExp,
 *   files: (path: string) => boolean,
 *   exempt?: (path: string) => boolean,
 *   hint: string,
 * }} Rule
 */

const isTs = (path) => path.endsWith('.ts') && !path.endsWith('.spec.ts');
const isTemplate = (path) => path.endsWith('.html');
const inMocks = (path) => path.includes(`${sep}mocks${sep}`);

/** @type {Rule[]} */
const RULES = [
  {
    id: 'no-client-side-scoring',
    description: 'O frontend não recalcula nota, XP, estrelas nem nível',
    // Arredondamento a menos de ~40 caracteres de um termo de gamificação.
    pattern:
      /Math\.(round|ceil|floor|trunc)\s*\([^)]{0,60}\b(stars?|xp|score|scorePercent|level|passed|correctAnswers)\b|(\b(stars?|xp|score|scorePercent|level)\b[^;\n]{0,40})Math\.(round|ceil|floor)/i,
    files: isTs,
    // O mock é o "servidor": é justamente ele quem aplica as regras.
    exempt: (path) => inMocks(path),
    hint: 'Estes valores vêm da API. Ver §11 da spec de integração e visão geral §3.1.',
  },
  {
    id: 'no-local-time-for-attempts',
    description: 'Cronômetro de tentativa não usa o relógio local',
    pattern: /Date\.now\(\)/,
    files: (path) =>
      isTs(path) && /attempt|tentativa|countdown/i.test(path) && !path.includes('server-clock'),
    exempt: (path) => inMocks(path),
    hint: 'Use ServerClock.remainingMs(expiresAt). Ver Parte 1, §7.',
  },
  {
    id: 'no-api-url-in-template',
    description: 'Arquivo privado não vai para href/src de template',
    pattern: /(href|src)\s*=\s*["'{][^"'}]*\/api\/v1/i,
    files: isTemplate,
    hint: 'Use <cc-secure-file-link>. Ver Parte 2, §4.3 e §11 da spec.',
  },
  {
    id: 'no-inner-html-in-template',
    description: 'innerHTML não é usado em template',
    pattern: /\[innerHTML\]/,
    files: isTemplate,
    hint: 'Use <cc-markdown>, que sanitiza com DOMPurify. Ver Parte 2, §4.3.',
  },
  {
    id: 'no-float-money',
    description: 'Valor monetário não é convertido para número no caminho de envio',
    pattern:
      /(parseFloat|Number)\s*\(\s*[^)]*\b(price|amount|value|numericValue|correctNumericValue|monetary)\b/i,
    files: isTs,
    exempt: (path) => inMocks(path) || path.includes(`util${sep}format`),
    hint: 'Valores monetários e respostas numéricas trafegam como string. Ver §2.1 da spec.',
  },
  {
    id: 'no-ngif-ngfor',
    description: 'Templates usam o fluxo de controle novo',
    pattern: /\*ngIf|\*ngFor|\[ngSwitch\]/,
    files: isTemplate,
    hint: 'Use @if / @for / @switch. Ver visão geral, §7.',
  },
];

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (entry !== 'node_modules') {
        out.push(...walk(full));
      }
    } else {
      out.push(full);
    }
  }
  return out;
}

function isAllowed(lines, index, ruleId) {
  const marker = `cc-allow: ${ruleId}`;
  return lines[index].includes(marker) || (index > 0 && lines[index - 1].includes(marker));
}

const files = walk(SRC);
const violations = [];

for (const rule of RULES) {
  for (const file of files) {
    if (!rule.files(file) || rule.exempt?.(file)) {
      continue;
    }

    const lines = readFileSync(file, 'utf8').split(/\r?\n/);
    lines.forEach((line, index) => {
      if (rule.pattern.test(line) && !isAllowed(lines, index, rule.id)) {
        violations.push({
          rule,
          file: relative(ROOT, file),
          line: index + 1,
          text: line.trim().slice(0, 120),
        });
      }
    });
  }
}

if (violations.length === 0) {
  console.log(`check:rules — ${RULES.length} regras, nenhuma violação.`);
  process.exit(0);
}

console.error(`check:rules — ${violations.length} violação(ões):\n`);
for (const v of violations) {
  console.error(`  ${v.file}:${v.line}`);
  console.error(`    [${v.rule.id}] ${v.rule.description}`);
  console.error(`    ${v.text}`);
  console.error(`    → ${v.rule.hint}\n`);
}
console.error(
  'Se a violação for legítima, marque a linha com o comentário "cc-allow: <id>" e explique o porquê.',
);
process.exit(1);
