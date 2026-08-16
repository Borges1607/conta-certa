import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Button } from 'primeng/button';
import { Drawer } from 'primeng/drawer';

import { GlobalProgressComponent } from '../global-progress/global-progress';
import { LogoComponent } from '../logo/logo';
import { OfflineBannerComponent } from '../offline-banner/offline-banner';
import { UserMenuComponent } from '../user-menu/user-menu';

export interface ShellNavItem {
  label: string;
  icon: string;
  route: string;
  /** Rota-índice precisa de correspondência exata para não ficar sempre ativa. */
  exact?: boolean;
}

/**
 * Shell com menu lateral — Parte 2, §3.3.
 *
 * Usado por professor e admin, que têm a mesma estrutura densa e diferem só
 * nos itens de menu. Abaixo de 1024px o menu vira gaveta.
 */
@Component({
  selector: 'cc-sidebar-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    Button,
    Drawer,
    GlobalProgressComponent,
    OfflineBannerComponent,
    UserMenuComponent,
    LogoComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './sidebar-shell.html',
  styleUrl: './sidebar-shell.scss',
})
export class SidebarShellComponent {
  readonly items = input.required<readonly ShellNavItem[]>();
  readonly areaLabel = input('');

  protected readonly drawerVisible = signal(false);
}
