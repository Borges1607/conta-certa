import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from 'primeng/button';
import { Message } from 'primeng/message';

/**
 * Conflito de versão — §3 e §11 da spec de integração.
 *
 * A única saída oferecida é **recarregar**. Não existe "salvar mesmo assim":
 * oferecer o botão seria oferecer a sobrescrita silenciosa que o critério de
 * aceite proíbe. Quem alterou o recurso não está aqui para se defender.
 */
@Component({
  selector: 'cc-version-conflict-notice',
  imports: [Message, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-message severity="warn" styleClass="conflict">
      <div class="conflict__body">
        <p class="conflict__text">
          {{ resourceLabel() }} foi alterado por outra pessoa depois que você abriu esta tela.
          Recarregue para ver os dados atuais e refazer a alteração — suas mudanças não foram
          salvas e nada foi sobrescrito.
        </p>
        <p-button
          label="Recarregar"
          icon="pi pi-refresh"
          size="small"
          [outlined]="true"
          [loading]="reloading()"
          [disabled]="reloading()"
          (onClick)="reload.emit()"
        />
      </div>
    </p-message>
  `,
  styles: `
    .conflict__body {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      align-items: flex-start;
    }
    .conflict__text {
      margin: 0;
      font-size: 0.875rem;
    }
  `,
})
export class VersionConflictNoticeComponent {
  /** Ex.: "Esta instituição", "Este professor". */
  readonly resourceLabel = input('Este registro');
  readonly reloading = input(false);

  readonly reload = output<void>();
}
