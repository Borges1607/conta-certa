import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Estrelas de uma lição — Parte 2, §4.4.
 *
 * Somente leitura: o valor vem da API e o frontend **não** calcula estrelas
 * (§11 da spec de integração). Sempre acompanhado de rótulo textual, porque
 * nenhum estado pode ser comunicado apenas por cor ou forma.
 */
@Component({
  selector: 'cc-star-rating',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="stars stars--{{ size() }}" [attr.aria-label]="ariaLabel()" role="img">
      @for (star of slots(); track $index) {
        <i
          class="pi"
          [class.pi-star-fill]="star"
          [class.pi-star]="!star"
          [class.stars__filled]="star"
          aria-hidden="true"
        ></i>
      }
      @if (showLabel()) {
        <span class="stars__label">{{ value() }}/{{ max() }}</span>
      }
    </span>
  `,
  styleUrl: './star-rating.scss',
})
export class StarRatingComponent {
  /** 0 a 3, conforme as faixas da §6.3 da spec. */
  readonly value = input.required<number>();
  readonly max = input(3);
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly showLabel = input(false);

  protected readonly slots = computed(() =>
    Array.from({ length: this.max() }, (_, i) => i < this.value()),
  );

  protected readonly ariaLabel = computed(() => {
    const value = this.value();
    return `${value} de ${this.max()} ${value === 1 ? 'estrela' : 'estrelas'}`;
  });
}
