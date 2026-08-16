import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewEncapsulation,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

import { MarkdownRenderer } from './markdown-renderer';

/**
 * Renderiza Markdown sanitizado com KaTeX — Parte 2, §4.3.
 *
 * `ViewEncapsulation.None` é necessário: o conteúdo é inserido como HTML e não
 * recebe os atributos de escopo do Angular, então estilos encapsulados não o
 * alcançariam. Os estilos ficam namespaced sob `.cc-markdown`.
 */
@Component({
  selector: 'cc-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  template: `<div class="cc-markdown" [class.cc-markdown--compact]="compact()" #target></div>`,
  styleUrl: './markdown.scss',
})
export class MarkdownComponent {
  private readonly renderer = inject(MarkdownRenderer);

  readonly content = input<string | null | undefined>('');
  readonly compact = input(false);

  private readonly target = viewChild.required<ElementRef<HTMLElement>>('target');
  private readonly html = signal('');

  /** Cada renderização recebe um número; só a mais recente pode escrever. */
  private renderToken = 0;

  constructor() {
    effect(() => {
      const source = this.content();
      const token = ++this.renderToken;

      void this.renderer.render(source).then((result) => {
        if (token === this.renderToken) {
          this.html.set(result);
        }
      });
    });

    effect(() => {
      const html = this.html();

      // Único ponto autorizado a escrever innerHTML no projeto. O valor vem de
      // MarkdownRenderer, que sempre devolve HTML já passado pelo DOMPurify.
      // Não usamos a ligação [innerHTML] do Angular porque o sanitizador dele
      // remove o atributo `style`, do qual o KaTeX depende para posicionar as
      // fórmulas — o resultado sairia visualmente quebrado.
      // eslint-disable-next-line no-restricted-syntax
      this.target().nativeElement.innerHTML = html;
    });
  }
}
