import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Message } from 'primeng/message';

/**
 * Selo e explicação de sala arquivada — Parte 5, §4.
 *
 * Sala arquivada é somente leitura em **todas** as abas. Este banner é a parte
 * visível disso; a parte funcional é o `readOnly` que cada aba propaga para
 * desabilitar os controles de mutação.
 */
@Component({
  selector: 'cc-archived-banner',
  imports: [Message],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-message severity="warn" styleClass="cc-archived-banner">
      <div class="cc-stack-sm">
        <strong>Sala arquivada</strong>
        <span class="cc-small">{{ message() }}</span>
      </div>
    </p-message>
  `,
})
export class ArchivedBannerComponent {
  readonly message = input(
    'Esta sala está somente leitura. Alunos não podem entrar nem fazer novas tentativas, e nada pode ser alterado na trilha, nas mídias ou na lista de alunos.',
  );
}
