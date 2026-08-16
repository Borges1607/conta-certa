import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Avatar } from 'primeng/avatar';
import { Button } from 'primeng/button';
import { Menu } from 'primeng/menu';

import { AuthStore } from '../../../core/auth/auth.store';
import { ROLE_LABELS } from '../../../core/models/labels';
import { initials } from '../../../core/util/format';
import { ThemeService } from '../theme.service';

/**
 * Menu do usuário — Parte 2, §3.1.
 *
 * Nome, e-mail, perfil, alternância de tema, conta e sair.
 */
@Component({
  selector: 'cc-user-menu',
  imports: [Avatar, Button, Menu],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (auth.user(); as user) {
      <p-button
        [text]="true"
        severity="secondary"
        styleClass="user-menu__trigger"
        [ariaLabel]="'Menu de ' + user.fullName"
        (onClick)="menu.toggle($event)"
      >
        <p-avatar [label]="avatarLabel()" shape="circle" size="normal" />
        <span class="user-menu__name cc-truncate">{{ user.fullName }}</span>
        <i class="pi pi-angle-down" aria-hidden="true"></i>
      </p-button>

      <p-menu #menu [model]="items()" [popup]="true" appendTo="body">
        <ng-template #start>
          <div class="user-menu__header">
            <strong class="cc-truncate">{{ user.fullName }}</strong>
            <small class="cc-muted cc-truncate">{{ user.email }}</small>
            <small class="cc-muted">{{ roleLabel() }}</small>
            @if (user.institution; as institution) {
              <small class="cc-muted cc-truncate">{{ institution.name }}</small>
            }
          </div>
        </ng-template>
      </p-menu>
    }
  `,
  styleUrl: './user-menu.scss',
})
export class UserMenuComponent {
  protected readonly auth = inject(AuthStore);
  private readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  protected readonly avatarLabel = computed(() => {
    const name = this.auth.user()?.fullName ?? '';
    return initials(name) || '?';
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_LABELS[role] : '';
  });

  protected readonly items = computed<MenuItem[]>(() => [
    { separator: true },
    {
      label: 'Minha conta',
      icon: 'pi pi-user',
      command: () => void this.router.navigate(['/conta/perfil']),
    },
    {
      label: 'Trocar senha',
      icon: 'pi pi-key',
      command: () => void this.router.navigate(['/conta/senha']),
    },
    {
      label: this.theme.isDark() ? 'Tema claro' : 'Tema escuro',
      icon: this.theme.isDark() ? 'pi pi-sun' : 'pi pi-moon',
      command: () => this.theme.toggle(),
    },
    { separator: true },
    {
      label: 'Sair',
      icon: 'pi pi-sign-out',
      command: () => void this.auth.logout(),
    },
  ]);
}
