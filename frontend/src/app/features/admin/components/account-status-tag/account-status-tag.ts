import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import type { AccountStatus } from '../../../../core/models/enums';
import { StatusTagComponent } from '../../../../shared/components/status-tag/status-tag';

/**
 * Situação da conta — Parte 6, §5.
 *
 * `PENDING` é âmbar e diz "Convite enviado", `ACTIVE` é verde e `INACTIVE` é
 * cinza. As cores e os rótulos vêm de `shared/components/status-tag` e de
 * `core/models/labels.ts`; este componente existe para fixar o `kind` e evitar
 * que alguma tela passe o enum errado.
 */
@Component({
  selector: 'cc-account-status-tag',
  imports: [StatusTagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<cc-status-tag [value]="status()" kind="AccountStatus" [rounded]="true" />`,
})
export class AccountStatusTagComponent {
  readonly status = input.required<AccountStatus>();
}
