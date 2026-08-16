import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ConnectionService } from '../connection.service';

/**
 * Aviso de conexão perdida — Parte 2, §3.1.
 *
 * A §9 da spec de integração pede o aviso "sem inventar tempo local": o texto
 * é deliberadamente sobre dados desatualizados, não sobre prazos. Quem decide
 * expiração é o servidor.
 */
@Component({
  selector: 'cc-offline-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!connection.isOnline()) {
      <div class="offline" role="status" aria-live="polite">
        <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
        <span>Você está sem conexão. Alguns dados podem estar desatualizados.</span>
      </div>
    }
  `,
  styleUrl: './offline-banner.scss',
})
export class OfflineBannerComponent {
  protected readonly connection = inject(ConnectionService);
}
