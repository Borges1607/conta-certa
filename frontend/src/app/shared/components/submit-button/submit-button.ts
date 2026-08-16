import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from 'primeng/button';

/**
 * Botão de mutação — Parte 2, §4.2.
 *
 * Liga-se ao signal `submitting` de um `SubmitGuard` (Parte 1, §10). Enquanto a
 * operação está em andamento, o botão fica desabilitado e em estado de carga:
 * é assim que o requisito "bloqueio contra duplo envio em mutações" se cumpre
 * de forma uniforme, sem cada tela reinventar o controle.
 */
@Component({
  selector: 'cc-submit-button',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-button
      [label]="label()"
      [icon]="icon()"
      [type]="type()"
      [severity]="severity()"
      [outlined]="outlined()"
      [text]="text()"
      [loading]="submitting()"
      [disabled]="submitting() || disabled()"
      [fluid]="fluid()"
      (onClick)="clicked.emit()"
    />
  `,
})
export class SubmitButtonComponent {
  readonly label = input.required<string>();
  /** O signal `submitting` do `SubmitGuard`. */
  readonly submitting = input.required<boolean>();

  readonly icon = input<string>('');
  readonly type = input<'button' | 'submit'>('submit');
  readonly severity = input<'primary' | 'secondary' | 'success' | 'danger' | 'warn'>('primary');
  readonly outlined = input(false);
  readonly text = input(false);
  readonly disabled = input(false);
  readonly fluid = input(false);

  readonly clicked = output<void>();
}
