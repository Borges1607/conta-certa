import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * XP e nível — Parte 2, §4.4.
 *
 * Ambos os valores vêm da API. O frontend **não** deriva nível de XP: a §11 da
 * spec de integração é explícita ao dizer que ele apenas apresenta os valores
 * retornados.
 *
 * Sempre relativo a uma sala (§6.4 da spec).
 */
@Component({
  selector: 'cc-xp-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="xp" [class.xp--compact]="compact()">
      <i class="pi pi-bolt" aria-hidden="true"></i>
      <span class="xp__value">{{ xp() }}</span>
      <span class="xp__unit">XP</span>
      @if (level() !== null) {
        <span class="xp__level">Nível {{ level() }}</span>
      }
    </span>
  `,
  styleUrl: './xp-badge.scss',
})
export class XpBadgeComponent {
  readonly xp = input.required<number>();
  readonly level = input<number | null>(null);
  readonly compact = input(false);
}
