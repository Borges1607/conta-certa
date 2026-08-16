import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from 'primeng/button';

/**
 * Estado vazio — Parte 2, §4.1.
 *
 * A §9 da spec de integração pede "vazio com ação contextual". Por isso
 * `actionLabel` é entrada **obrigatória**: um vazio sem saída é um beco sem
 * saída, e o compilador não deixa criar um.
 */
@Component({
  selector: 'cc-empty-state',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <i class="empty__icon {{ icon() }}" aria-hidden="true"></i>
      <h3 class="empty__title">{{ title() }}</h3>
      @if (message()) {
        <p class="empty__message">{{ message() }}</p>
      }
      <p-button
        [label]="actionLabel()"
        [icon]="actionIcon()"
        [outlined]="true"
        (onClick)="action.emit()"
      />
    </div>
  `,
  styleUrl: './empty-state.scss',
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  /** Obrigatório: todo vazio precisa de uma saída. */
  readonly actionLabel = input.required<string>();

  readonly message = input<string>('');
  readonly icon = input('pi pi-inbox');
  readonly actionIcon = input('pi pi-plus');

  readonly action = output<void>();
}
