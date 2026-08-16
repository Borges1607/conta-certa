import { ChangeDetectionStrategy, Component } from '@angular/core';

import {
  SidebarShellComponent,
  type ShellNavItem,
} from '../../../../shared/layout/sidebar-shell/sidebar-shell';

/**
 * Shell do professor — Parte 2, §3.3.
 *
 * Reutiliza o `cc-sidebar-shell`: professor e admin têm a mesma estrutura densa
 * e diferem apenas nos itens de menu.
 */
@Component({
  selector: 'cc-teacher-shell',
  imports: [SidebarShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<cc-sidebar-shell [items]="items" areaLabel="Área do professor" />`,
})
export class TeacherShellComponent {
  protected readonly items: readonly ShellNavItem[] = [
    { label: 'Painel', icon: 'pi pi-home', route: '/professor', exact: true },
    { label: 'Salas', icon: 'pi pi-users', route: '/professor/salas' },
    { label: 'Lições', icon: 'pi pi-book', route: '/professor/licoes' },
    { label: 'Videoaulas', icon: 'pi pi-video', route: '/professor/videos' },
    { label: 'Materiais', icon: 'pi pi-file', route: '/professor/materiais' },
    { label: 'Relatórios', icon: 'pi pi-chart-bar', route: '/professor/relatorios' },
  ];
}
