import { ChangeDetectionStrategy, Component } from '@angular/core';

import {
  SidebarShellComponent,
  type ShellNavItem,
} from '../../../../shared/layout/sidebar-shell/sidebar-shell';

/**
 * Moldura da área do administrador — Parte 6, §2.
 *
 * O menu é a leitura literal da matriz da §10 da spec de integração: o admin
 * gerencia instituições, professores e dicas, e **nada mais**. Não há salas,
 * lições, questões, mídias nem ranking aqui — não porque estejam escondidos,
 * mas porque o admin não tem essas capacidades. Um item a mais neste array
 * seria uma promessa que a API recusa com `403`.
 */
const ADMIN_NAV: readonly ShellNavItem[] = [
  { label: 'Painel', icon: 'pi pi-home', route: '/admin', exact: true },
  { label: 'Instituições', icon: 'pi pi-building', route: '/admin/instituicoes' },
  { label: 'Professores', icon: 'pi pi-users', route: '/admin/professores' },
  { label: 'Dicas financeiras', icon: 'pi pi-lightbulb', route: '/admin/dicas' },
];

@Component({
  selector: 'cc-admin-shell',
  imports: [SidebarShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<cc-sidebar-shell [items]="items" areaLabel="Administração" />`,
})
export class AdminShellComponent {
  protected readonly items = ADMIN_NAV;
}
