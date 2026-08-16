import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Cartão padrão das telas públicas. */
@Component({
  selector: 'cc-auth-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card cc-card cc-card-lg">
      <h1 class="card__title">{{ heading() }}</h1>
      @if (description()) {
        <p class="card__description">{{ description() }}</p>
      }
      <ng-content />
    </section>
  `,
  styleUrl: './auth-card.scss',
})
export class AuthCardComponent {
  readonly heading = input.required<string>();
  readonly description = input('');
}
