import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { AdminDashboardService } from '../../data/admin-dashboard.service';

/** Um cartão do painel: número, rótulo e o recorte de lista que ele abre. */
interface DashboardCard {
  key: string;
  label: string;
  value: number;
  icon: string;
  route: string;
  /** Vai para a query string — é o que torna o link compartilhável. */
  queryParams: Record<string, string>;
  tone: 'neutral' | 'success' | 'warn' | 'muted';
}

/**
 * Painel do administrador — Parte 6, §3.
 *
 * Cada cartão é um link de verdade, com o filtro na query string. Isso não é
 * detalhe de implementação: é o que faz "instituições inativas" ser um endereço
 * que se copia para outra pessoa e que sobrevive a uma recarga da página.
 */
@Component({
  selector: 'cc-admin-dashboard-page',
  imports: [RouterLink, PageHeaderComponent, LoadingSkeletonComponent, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class AdminDashboardPage {
  private readonly service = inject(AdminDashboardService);

  protected readonly state = createPageState(() => this.service.load());

  protected readonly institutionCards = computed<DashboardCard[]>(() => {
    const data = this.state.data();
    if (!data) {
      return [];
    }
    return [
      {
        key: 'institutions-total',
        label: 'Instituições cadastradas',
        value: data.institutions.total,
        icon: 'pi pi-building',
        route: '/admin/instituicoes',
        queryParams: {} as Record<string, string>,
        tone: 'neutral',
      },
      {
        key: 'institutions-active',
        label: 'Ativas',
        value: data.institutions.active,
        icon: 'pi pi-check-circle',
        route: '/admin/instituicoes',
        queryParams: { active: 'true' },
        tone: 'success',
      },
      {
        key: 'institutions-inactive',
        label: 'Inativas',
        value: data.institutions.inactive,
        icon: 'pi pi-ban',
        route: '/admin/instituicoes',
        queryParams: { active: 'false' },
        tone: 'muted',
      },
    ];
  });

  protected readonly teacherCards = computed<DashboardCard[]>(() => {
    const data = this.state.data();
    if (!data) {
      return [];
    }
    return [
      {
        key: 'teachers-total',
        label: 'Professores cadastrados',
        value: data.teachers.total,
        icon: 'pi pi-users',
        route: '/admin/professores',
        queryParams: {} as Record<string, string>,
        tone: 'neutral',
      },
      {
        key: 'teachers-pending',
        label: 'Convite enviado',
        value: data.teachers.pending,
        icon: 'pi pi-envelope',
        route: '/admin/professores',
        queryParams: { status: 'PENDING' },
        tone: 'warn',
      },
      {
        key: 'teachers-active',
        label: 'Ativos',
        value: data.teachers.active,
        icon: 'pi pi-check-circle',
        route: '/admin/professores',
        queryParams: { status: 'ACTIVE' },
        tone: 'success',
      },
      {
        key: 'teachers-inactive',
        label: 'Inativos',
        value: data.teachers.inactive,
        icon: 'pi pi-ban',
        route: '/admin/professores',
        queryParams: { status: 'INACTIVE' },
        tone: 'muted',
      },
    ];
  });

  constructor() {
    void this.state.load();
  }

  protected retry(): void {
    void this.state.retry();
  }
}
