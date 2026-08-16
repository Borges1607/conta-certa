import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from 'primeng/tag';

/**
 * Situação de um recurso com `active: boolean`.
 *
 * `cc-status-tag` cobre os enums do contrato; instituições e dicas usam um
 * booleano, que não tem entrada em `core/models/labels.ts`. O rótulo textual
 * acompanha a cor sempre — nenhum estado é comunicado só por cor.
 */
@Component({
  selector: 'cc-active-tag',
  imports: [Tag],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-tag [value]="label()" [severity]="severity()" [rounded]="true" />`,
})
export class ActiveTagComponent {
  readonly active = input.required<boolean>();
  readonly activeLabel = input('Ativa');
  readonly inactiveLabel = input('Inativa');

  protected readonly label = computed(() =>
    this.active() ? this.activeLabel() : this.inactiveLabel(),
  );

  protected readonly severity = computed<'success' | 'secondary'>(() =>
    this.active() ? 'success' : 'secondary',
  );
}
