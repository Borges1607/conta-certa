import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from 'primeng/button';
import { Message } from 'primeng/message';

/**
 * Conflito de versão dentro de um formulário — visão geral, §3.6.
 *
 * A única ação oferecida é **Recarregar**. "Salvar por cima" não existe aqui e
 * não deve existir em lugar nenhum: sobrescrever silenciosamente é exatamente o
 * que o critério de aceite proíbe.
 */
@Component({
  selector: 'cc-version-conflict-notice',
  imports: [Button, Message],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-message severity="warn">
      <div class="cc-stack-sm">
        <strong>{{ title() }}</strong>
        <span class="cc-small">{{ message() }}</span>
        <div>
          <p-button
            label="Recarregar"
            icon="pi pi-refresh"
            severity="warn"
            [outlined]="true"
            size="small"
            [loading]="reloading()"
            [disabled]="reloading()"
            (onClick)="reload.emit()"
          />
        </div>
      </div>
    </p-message>
  `,
})
export class VersionConflictNoticeComponent {
  readonly title = input('Esta sala foi alterada em outro lugar');
  readonly message = input(
    'Alguém — ou você, em outra aba — salvou uma versão mais nova. Recarregue para ver o conteúdo atual antes de editar de novo.',
  );
  readonly reloading = input(false);

  readonly reload = output<void>();
}
