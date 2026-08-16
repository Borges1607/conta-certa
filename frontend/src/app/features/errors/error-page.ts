import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Location } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';

import { AuthStore } from '../../core/auth/auth.store';
import { LogoComponent } from '../../shared/layout/logo/logo';

/**
 * Páginas `/403` e `/404`.
 *
 * O `kind` vem da configuração de rota (`data`), ligado por
 * `withComponentInputBinding`.
 */
@Component({
  selector: 'cc-error-page',
  imports: [Button, RouterLink, LogoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error-page">
      <cc-logo size="md" />

      @if (kind() === 'forbidden') {
        <i class="pi pi-lock error-page__icon" aria-hidden="true"></i>
        <h1>Sem permissão</h1>
        <p>Esta área não faz parte do seu perfil de acesso.</p>
      } @else {
        <i class="pi pi-compass error-page__icon" aria-hidden="true"></i>
        <h1>Página não encontrada</h1>
        <p>O endereço que você abriu não existe ou foi movido.</p>
      }

      <div class="error-page__actions">
        <p-button label="Voltar" icon="pi pi-arrow-left" [outlined]="true" (onClick)="goBack()" />
        <p-button [label]="homeLabel()" icon="pi pi-home" [routerLink]="home()" />
      </div>
    </div>
  `,
  styleUrl: './error-page.scss',
})
export class ErrorPage {
  private readonly location = inject(Location);
  private readonly auth = inject(AuthStore);

  readonly kind = input<'forbidden' | 'not-found'>('not-found');

  protected home(): string {
    return this.auth.isAuthenticated() ? this.auth.homePath() : '/login';
  }

  protected homeLabel(): string {
    return this.auth.isAuthenticated() ? 'Ir para o início' : 'Ir para o login';
  }

  protected goBack(): void {
    this.location.back();
  }
}
