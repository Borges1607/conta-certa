import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { MarkdownRenderer } from './markdown-renderer';

/**
 * Protege o critério da §11 da spec de integração: "conteúdo Markdown é
 * sanitizado, renderiza KaTeX e não executa HTML ou scripts".
 */
describe('MarkdownRenderer', () => {
  let renderer: MarkdownRenderer;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    renderer = TestBed.inject(MarkdownRenderer);
  });

  describe('sanitização', () => {
    it('remove <script>', async () => {
      const html = await renderer.render('Olá <script>alert("xss")</script> mundo');
      expect(html).not.toContain('<script');
      expect(html).not.toContain('alert');
    });

    it('remove atributos de evento', async () => {
      const html = await renderer.render('<img src="x" onerror="alert(1)">');
      expect(html).not.toContain('onerror');
      expect(html).not.toContain('alert');
    });

    it('remove href javascript:', async () => {
      const html = await renderer.render('[clique](javascript:alert(1))');
      expect(html).not.toContain('javascript:');
    });

    it('remove <iframe>', async () => {
      const html = await renderer.render('<iframe src="https://evil.example"></iframe>');
      expect(html).not.toContain('<iframe');
    });

    it('remove <style> e <form>', async () => {
      const html = await renderer.render(
        '<style>body{display:none}</style><form action="/x"><input name="a"></form>',
      );
      expect(html).not.toContain('<style');
      expect(html).not.toContain('<form');
      expect(html).not.toContain('<input');
    });

    it('remove svg com script embutido', async () => {
      const html = await renderer.render('<svg><script>alert(1)</script></svg>');
      expect(html).not.toContain('alert');
    });

    it('endurece links externos', async () => {
      const html = await renderer.render('[site](https://exemplo.com)');
      expect(html).toContain('rel="noopener noreferrer nofollow"');
      expect(html).toContain('target="_blank"');
    });
  });

  describe('renderização', () => {
    it('renderiza fórmula KaTeX em linha', async () => {
      const html = await renderer.render('A fórmula $E=mc^2$ é famosa.');
      expect(html).toContain('katex');
      expect(html).not.toContain('$E=mc^2$');
    });

    it('renderiza fórmula KaTeX em bloco', async () => {
      const html = await renderer.render('$$M=C(1+i)^t$$');
      expect(html).toContain('katex-display');
    });

    it('não transforma cifrão dentro de bloco de código em fórmula', async () => {
      const html = await renderer.render('```\nconst preco = $valor;\n```');
      expect(html).toContain('<pre>');
      expect(html).not.toContain('katex');
    });

    it('renderiza tabela', async () => {
      const html = await renderer.render('| a | b |\n|---|---|\n| 1 | 2 |');
      expect(html).toContain('<table>');
      expect(html).toContain('<td>1</td>');
    });

    it('renderiza bloco de código', async () => {
      const html = await renderer.render('```js\nconst x = 1;\n```');
      expect(html).toContain('<code');
      expect(html).toContain('const x = 1;');
    });

    it('não quebra com fórmula inválida', async () => {
      const html = await renderer.render('$\\frac{1}{$');
      expect(typeof html).toBe('string');
    });

    it('devolve vazio para conteúdo vazio ou nulo', async () => {
      expect(await renderer.render('')).toBe('');
      expect(await renderer.render(null)).toBe('');
      expect(await renderer.render('   ')).toBe('');
    });
  });
});
