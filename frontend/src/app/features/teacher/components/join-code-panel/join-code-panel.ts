import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Button } from 'primeng/button';

import { NotificationService } from '../../../../core/notifications/notification.service';

/**
 * Código de ingresso em destaque — Parte 5, §4.
 *
 * `navigator.clipboard` não existe fora de contexto seguro e pode ser negado
 * pelo usuário; nesses casos o código é **selecionado** para que um `Ctrl+C`
 * resolva. Nunca fica sem saída.
 */
@Component({
  selector: 'cc-join-code-panel',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './join-code-panel.html',
  styleUrl: './join-code-panel.scss',
})
export class JoinCodePanelComponent {
  private readonly notify = inject(NotificationService);

  readonly code = input.required<string>();
  /** Sala arquivada não regenera código. */
  readonly readOnly = input(false);
  readonly regenerating = input(false);

  readonly regenerate = output<void>();

  protected readonly copied = signal(false);

  private readonly codeElement = viewChild.required<ElementRef<HTMLElement>>('codeText');

  protected async copy(): Promise<void> {
    const code = this.code();

    try {
      await navigator.clipboard.writeText(code);
      this.copied.set(true);
      this.notify.success('Código copiado', `Compartilhe ${code} com a turma.`);
      setTimeout(() => this.copied.set(false), 2500);
    } catch {
      this.selectCode();
      this.notify.info(
        'Copie manualmente',
        'O navegador bloqueou a cópia automática. O código já está selecionado: use Ctrl+C.',
      );
    }
  }

  /** Fallback: seleciona o texto para uma cópia manual. */
  private selectCode(): void {
    const element = this.codeElement().nativeElement;
    const range = document.createRange();
    range.selectNodeContents(element);

    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
  }
}
