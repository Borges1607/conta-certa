import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Button } from 'primeng/button';

import { ApiError } from '../../../core/api/problem-details';

interface ErrorPresentation {
  icon: string;
  title: string;
  actionLabel: string;
  /** `retry` refaz a consulta; `reload` recarrega dados desatualizados. */
  actionKind: 'retry' | 'reload' | 'back' | 'login';
  tone: 'neutral' | 'warning' | 'danger';
}

/**
 * Estado de erro — Parte 2, §4.1.
 *
 * A §9 da spec de integração exige que `401`, `403`, `404` e `409` tenham
 * mensagens distintas. Aqui eles também têm **ações distintas**, que é o que
 * realmente muda a vida de quem está na tela.
 *
 * O `409` nunca oferece "salvar por cima": o critério da §11 proíbe sobrescrita
 * silenciosa, e oferecer o botão seria convidar para ela.
 */
@Component({
  selector: 'cc-error-state',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error error--{{ presentation().tone }}" role="alert">
      <i class="error__icon {{ presentation().icon }}" aria-hidden="true"></i>
      <h3 class="error__title">{{ presentation().title }}</h3>
      <p class="error__message">{{ error().detail }}</p>

      @if (error().traceId; as traceId) {
        <p class="error__trace cc-xs cc-muted">Código de referência: {{ traceId }}</p>
      }

      <p-button
        [label]="presentation().actionLabel"
        [icon]="actionIcon()"
        [outlined]="true"
        (onClick)="onAction()"
      />
    </div>
  `,
  styleUrl: './error-state.scss',
})
export class ErrorStateComponent {
  readonly error = input.required<ApiError>();

  readonly retry = output<void>();
  readonly back = output<void>();
  readonly login = output<void>();

  protected readonly presentation = computed<ErrorPresentation>(() => {
    const error = this.error();

    if (error.isOffline) {
      return {
        icon: 'pi pi-wifi',
        title: 'Sem conexão',
        actionLabel: 'Tentar novamente',
        actionKind: 'retry',
        tone: 'warning',
      };
    }

    switch (error.status) {
      case 401:
        return {
          icon: 'pi pi-sign-in',
          title: 'Sessão expirada',
          actionLabel: 'Entrar novamente',
          actionKind: 'login',
          tone: 'warning',
        };
      case 403:
        return {
          icon: 'pi pi-lock',
          title: 'Sem permissão',
          actionLabel: 'Voltar',
          actionKind: 'back',
          tone: 'warning',
        };
      case 404:
        return {
          icon: 'pi pi-search',
          title: 'Não encontrado',
          actionLabel: 'Voltar',
          actionKind: 'back',
          tone: 'neutral',
        };
      case 409:
        return {
          icon: 'pi pi-history',
          title: 'Dados desatualizados',
          actionLabel: 'Recarregar',
          actionKind: 'reload',
          tone: 'warning',
        };
      case 410:
        return {
          icon: 'pi pi-clock',
          title: 'Não está mais disponível',
          actionLabel: 'Voltar',
          actionKind: 'back',
          tone: 'neutral',
        };
      case 429:
        return {
          icon: 'pi pi-hourglass',
          title: 'Muitas tentativas',
          actionLabel: 'Tentar novamente',
          actionKind: 'retry',
          tone: 'warning',
        };
      default:
        return {
          icon: 'pi pi-exclamation-triangle',
          title: 'Algo deu errado',
          actionLabel: 'Tentar novamente',
          actionKind: 'retry',
          tone: 'danger',
        };
    }
  });

  protected readonly actionIcon = computed(() => {
    switch (this.presentation().actionKind) {
      case 'back':
        return 'pi pi-arrow-left';
      case 'login':
        return 'pi pi-sign-in';
      default:
        return 'pi pi-refresh';
    }
  });

  protected onAction(): void {
    switch (this.presentation().actionKind) {
      case 'back':
        this.back.emit();
        break;
      case 'login':
        this.login.emit();
        break;
      default:
        this.retry.emit();
    }
  }
}
