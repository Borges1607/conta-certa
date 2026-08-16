import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { GlobalProgressComponent } from '../../../../shared/layout/global-progress/global-progress';
import { LogoComponent } from '../../../../shared/layout/logo/logo';
import { OfflineBannerComponent } from '../../../../shared/layout/offline-banner/offline-banner';

/**
 * Moldura das telas públicas — Parte 3, §2.
 *
 * Coluna centralizada, sem shell de perfil: antes do login não há perfil.
 */
@Component({
  selector: 'cc-public-shell',
  imports: [RouterOutlet, GlobalProgressComponent, OfflineBannerComponent, LogoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cc-global-progress />
    <cc-offline-banner />

    <div class="public">
      <header class="public__brand">
        <cc-logo size="lg" />
        <p class="public__tagline">Educação financeira gamificada</p>
      </header>

      <main class="public__content">
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './public-shell.scss',
})
export class PublicShellComponent {}
