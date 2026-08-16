import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface Crumb {
  label: string;
  /** Ausente no último item. */
  link?: string;
}

/**
 * Cabeçalho de página com trilha e ações — Parte 2, §4.4.
 *
 * As ações entram por projeção de conteúdo, para o cabeçalho não precisar
 * conhecer nada da feature.
 */
@Component({
  selector: 'cc-page-header',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="header">
      @if (crumbs().length > 0) {
        <nav class="header__crumbs" aria-label="Trilha de navegação">
          <ol>
            @for (crumb of crumbs(); track crumb.label; let last = $last) {
              <li>
                @if (crumb.link && !last) {
                  <a [routerLink]="crumb.link">{{ crumb.label }}</a>
                  <i class="pi pi-angle-right" aria-hidden="true"></i>
                } @else {
                  <span [attr.aria-current]="last ? 'page' : null">{{ crumb.label }}</span>
                }
              </li>
            }
          </ol>
        </nav>
      }

      <div class="header__main">
        <div class="header__text">
          <h1 class="header__title">{{ title() }}</h1>
          @if (subtitle()) {
            <p class="header__subtitle">{{ subtitle() }}</p>
          }
        </div>
        <div class="header__actions">
          <ng-content />
        </div>
      </div>
    </header>
  `,
  styleUrl: './page-header.scss',
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly crumbs = input<readonly Crumb[]>([]);
}
