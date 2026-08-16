import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Toast } from 'primeng/toast';

/**
 * Raiz da aplicação.
 *
 * `p-toast` e `p-confirmdialog` são montados aqui uma única vez — nenhuma
 * página deve declarar os seus (Parte 2, §3.1).
 */
@Component({
  selector: 'cc-root',
  imports: [RouterOutlet, Toast, ConfirmDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-toast position="top-right" [breakpoints]="{ '640px': { width: '90vw', right: '5vw' } }" />
    <p-confirmdialog />
    <router-outlet />
  `,
})
export class App {}