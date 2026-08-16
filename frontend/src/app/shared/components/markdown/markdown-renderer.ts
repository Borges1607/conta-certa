import { Injectable } from '@angular/core';
import type { TokenizerAndRendererExtension, Tokens } from 'marked';

/**
 * Renderização de Markdown — Parte 2, §4.3.
 *
 * Este é um componente de segurança. A §2.1 da spec de integração é explícita:
 * o conteúdo aceita KaTeX, tabelas e blocos de código, e **HTML bruto não deve
 * ser renderizado**. O critério da §11 reforça: "conteúdo Markdown é
 * sanitizado, renderiza KaTeX e não executa HTML ou scripts".
 *
 * O caminho é sempre: marked (com extensão de matemática) → KaTeX →
 * DOMPurify. A sanitização é a **última** etapa e vale sobre o HTML final,
 * inclusive sobre a saída do KaTeX. Nada escapa dela.
 *
 * As bibliotecas entram por import dinâmico: só quem abre uma tela com
 * conteúdo paga o custo (Parte 7, §5).
 */

interface MathToken extends Tokens.Generic {
  type: string;
  raw: string;
  text: string;
}

interface RenderLibs {
  parse: (markdown: string) => string;
  sanitize: (html: string) => string;
}

/** Tags que o KaTeX emite e que precisam sobreviver à sanitização. */
const KATEX_TAGS = [
  'math',
  'semantics',
  'mrow',
  'mi',
  'mn',
  'mo',
  'ms',
  'mtext',
  'mspace',
  'msup',
  'msub',
  'msubsup',
  'mfrac',
  'msqrt',
  'mroot',
  'mover',
  'munder',
  'munderover',
  'mtable',
  'mtr',
  'mtd',
  'mpadded',
  'mphantom',
  'menclose',
  'mstyle',
  'annotation',
  'annotation-xml',
];

@Injectable({ providedIn: 'root' })
export class MarkdownRenderer {
  private libs: Promise<RenderLibs> | null = null;

  /** Devolve HTML **já sanitizado**, pronto para ser inserido no DOM. */
  async render(markdown: string | null | undefined): Promise<string> {
    if (!markdown?.trim()) {
      return '';
    }

    const { parse, sanitize } = await this.load();
    return sanitize(parse(markdown));
  }

  private load(): Promise<RenderLibs> {
    this.libs ??= this.buildLibs();
    return this.libs;
  }

  private async buildLibs(): Promise<RenderLibs> {
    const [markedModule, purifyModule, katexModule] = await Promise.all([
      import('marked'),
      import('dompurify'),
      import('katex'),
    ]);

    const katex = katexModule.default;
    const purify = purifyModule.default;
    const marked = new markedModule.Marked({ gfm: true, breaks: false });

    const renderMath = (expression: string, displayMode: boolean): string => {
      try {
        return katex.renderToString(expression, {
          displayMode,
          throwOnError: false,
          output: 'htmlAndMathml',
          // `trust: false` é o padrão e bloqueia \href, \url e \includegraphics.
          // Mantido explícito porque é uma decisão de segurança, não um detalhe.
          trust: false,
          strict: false,
        });
      } catch {
        // Fórmula inválida vira texto, nunca quebra a página do aluno.
        return escapeHtml(expression);
      }
    };

    marked.use({ extensions: mathExtensions(renderMath) });

    purify.addHook('afterSanitizeAttributes', (node) => {
      if (node instanceof HTMLElement && node.tagName === 'A' && node.hasAttribute('href')) {
        node.setAttribute('target', '_blank');
        node.setAttribute('rel', 'noopener noreferrer nofollow');
      }
    });

    return {
      parse: (markdown) => marked.parse(markdown, { async: false }),
      sanitize: (html) =>
        purify.sanitize(html, {
          USE_PROFILES: { html: true, mathMl: true, svg: true },
          ADD_TAGS: KATEX_TAGS,
          ADD_ATTR: ['xmlns', 'encoding', 'display', 'mathvariant', 'stretchy', 'scriptlevel'],
          // Nunca renderizamos HTML bruto perigoso, independentemente da origem.
          FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input', 'base'],
          FORBID_ATTR: ['srcdoc', 'formaction', 'ping'],
          ALLOW_DATA_ATTR: false,
        }),
    };
  }
}

/**
 * Extensões de matemática para o marked.
 *
 * Usar extensões — em vez de recortar `$...$` do texto antes de parsear — é o
 * que faz cifrões dentro de bloco de código continuarem sendo cifrões: o
 * tokenizador do marked processa código antes de chegar aqui.
 */
function mathExtensions(
  renderMath: (expression: string, displayMode: boolean) => string,
): TokenizerAndRendererExtension[] {
  return [
    {
      name: 'blockMath',
      level: 'block',
      start: (src: string) => src.indexOf('$$'),
      tokenizer(src: string) {
        const match = /^\$\$([\s\S]+?)\$\$/.exec(src);
        if (!match) {
          return undefined;
        }
        return { type: 'blockMath', raw: match[0], text: match[1].trim() };
      },
      renderer: (token) => renderMath((token as MathToken).text, true),
    },
    {
      name: 'inlineMath',
      level: 'inline',
      start: (src: string) => src.indexOf('$'),
      tokenizer(src: string) {
        const match = /^\$([^$\n]+?)\$/.exec(src);
        if (!match) {
          return undefined;
        }
        return { type: 'inlineMath', raw: match[0], text: match[1] };
      },
      renderer: (token) => renderMath((token as MathToken).text, false),
    },
  ];
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
