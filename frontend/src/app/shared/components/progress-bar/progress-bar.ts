import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ProgressBar } from 'primeng/progressbar';

/**
 * Barra de progresso com rótulo — Parte 2, §4.4.
 *
 * O percentual vem da API, de 0 a 100 (§2.1 da spec). O componente apenas
 * limita a faixa para não quebrar o desenho se vier algo fora dela.
 */
@Component({
  selector: 'cc-progress-bar',
  imports: [ProgressBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="progress">
      @if (label()) {
        <div class="progress__header">
          <span class="progress__label">{{ label() }}</span>
          <span class="progress__value">{{ safeValue() }}%</span>
        </div>
      }
      <p-progressbar
        [value]="safeValue()"
        [showValue]="false"
        [style]="{ height: height() }"
        [attr.aria-label]="ariaLabel()"
      />
    </div>
  `,
  styleUrl: './progress-bar.scss',
})
export class ProgressBarComponent {
  readonly value = input.required<number>();
  readonly label = input('');
  readonly height = input('0.6rem');

  protected readonly safeValue = computed(() => Math.max(0, Math.min(100, Math.round(this.value()))));

  protected readonly ariaLabel = computed(
    () => `${this.label() || 'Progresso'}: ${this.safeValue()}%`,
  );
}
