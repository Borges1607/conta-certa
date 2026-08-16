import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Marca do Conta Certa. */
@Component({
  selector: 'cc-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="logo logo--{{ size() }}">
      <svg viewBox="0 0 32 32" class="logo__mark" aria-hidden="true">
        <rect x="1" y="1" width="30" height="30" rx="9" fill="url(#ccGradient)" />
        <path
          d="M16 7v18M12.5 11.5c0-1.7 1.6-2.6 3.5-2.6s3.5.9 3.5 2.6-1.6 2.3-3.5 2.9-3.5 1.2-3.5 3 1.6 2.7 3.5 2.7 3.5-1 3.5-2.7"
          stroke="#fff"
          stroke-width="2"
          stroke-linecap="round"
          fill="none"
        />
        <defs>
          <linearGradient id="ccGradient" x1="0" y1="0" x2="32" y2="32">
            <stop offset="0%" stop-color="#7c3aed" />
            <stop offset="100%" stop-color="#2563eb" />
          </linearGradient>
        </defs>
      </svg>
      @if (showName()) {
        <span class="logo__name">Conta Certa</span>
      }
      <span class="cc-sr-only">Conta Certa</span>
    </span>
  `,
  styleUrl: './logo.scss',
})
export class LogoComponent {
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly showName = input(true);
}
