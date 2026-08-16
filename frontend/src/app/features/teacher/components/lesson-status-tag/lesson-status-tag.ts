import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import type { ContentStatus } from '../../../../core/models/enums';
import { StatusTagComponent } from '../../../../shared/components/status-tag/status-tag';

/**
 * Ciclo de vida do conteúdo com estados visualmente distintos — Parte 5, §6.1.
 *
 * Envolve o `cc-status-tag` para acrescentar a explicação do que cada estado
 * significa na prática, que é o que o professor precisa saber antes de
 * publicar ou arquivar.
 */
@Component({
  selector: 'cc-lesson-status-tag',
  imports: [StatusTagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="cc-row-sm" [title]="explanation()">
      <cc-status-tag [value]="status()" kind="ContentStatus" [rounded]="true" />
      @if (showExplanation()) {
        <small class="cc-muted">{{ explanation() }}</small>
      }
    </span>
  `,
})
export class LessonStatusTagComponent {
  readonly status = input.required<ContentStatus>();
  readonly showExplanation = input(false);

  protected readonly explanation = computed(() => {
    switch (this.status()) {
      case 'DRAFT':
        return 'Visível só para você. Não pode ser usada em salas.';
      case 'PUBLISHED':
        return 'Disponível para ser atribuída às suas salas.';
      case 'ARCHIVED':
        return 'Somente leitura. Continua nas salas que já a usam.';
    }
  });
}
