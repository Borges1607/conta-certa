import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { LoadingIndicator } from '../../../core/interceptors/loading.interceptor';

/**
 * Barra de progresso global — Parte 2, §3.1.
 *
 * Ligada ao contador do `loadingInterceptor`. Requisições marcadas como
 * silenciosas — polling, refresh, atualização em segundo plano — não a acendem.
 */
@Component({
  selector: 'cc-global-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading.isLoading()) {
      <div class="bar" role="progressbar" aria-label="Carregando"></div>
    }
  `,
  styleUrl: './global-progress.scss',
})
export class GlobalProgressComponent {
  protected readonly loading = inject(LoadingIndicator);
}
