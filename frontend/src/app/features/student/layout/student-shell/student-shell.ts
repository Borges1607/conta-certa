import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Select } from 'primeng/select';

import { GlobalProgressComponent } from '../../../../shared/layout/global-progress/global-progress';
import { LogoComponent } from '../../../../shared/layout/logo/logo';
import { OfflineBannerComponent } from '../../../../shared/layout/offline-banner/offline-banner';
import { UserMenuComponent } from '../../../../shared/layout/user-menu/user-menu';
import { StarRatingComponent } from '../../../../shared/components/star-rating/star-rating';
import { XpBadgeComponent } from '../../../../shared/components/xp-badge/xp-badge';
import { RoomContextStore } from '../../data/room-context.store';

interface RoomOption {
  id: string;
  name: string;
}

interface NavItem {
  label: string;
  shortLabel: string;
  icon: string;
  path: string;
  /** A visão geral é a rota-índice: sem correspondência exata ficaria sempre ativa. */
  exact: boolean;
}

/**
 * Shell do aluno — Parte 2, §3.2 (implementado aqui por depender do contexto
 * de sala, que pertence à Parte 4).
 *
 * O seletor de sala **navega**: ele troca o `roomId` da URL e nunca substitui
 * dados em memória. É o que garante que XP, progresso e ranking nunca se
 * misturem entre salas, inclusive no botão "voltar" do navegador.
 */
@Component({
  selector: 'cc-student-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    FormsModule,
    Select,
    GlobalProgressComponent,
    OfflineBannerComponent,
    UserMenuComponent,
    LogoComponent,
    XpBadgeComponent,
    StarRatingComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-shell.html',
  styleUrl: './student-shell.scss',
})
export class StudentShellComponent {
  private readonly router = inject(Router);

  protected readonly context = inject(RoomContextStore);

  protected readonly roomId = this.context.roomId;
  protected readonly dashboard = this.context.dashboard.data;

  protected readonly roomOptions = computed<RoomOption[]>(
    () => this.context.rooms.data()?.map((room) => ({ id: room.id, name: room.name })) ?? [],
  );

  /** Só mostramos XP e estrelas quando pertencem à sala que está na URL. */
  protected readonly stats = computed(() => {
    const data = this.dashboard();
    return data && data.room.id === this.roomId() ? data : null;
  });

  protected readonly navItems = computed<NavItem[]>(() => {
    const roomId = this.roomId();
    if (!roomId) {
      return [];
    }
    const base = `/aluno/salas/${roomId}`;
    return [
      { label: 'Visão geral', shortLabel: 'Início', icon: 'pi pi-home', path: base, exact: true },
      { label: 'Trilha', shortLabel: 'Trilha', icon: 'pi pi-map', path: `${base}/trilha`, exact: false },
      { label: 'Videoaulas', shortLabel: 'Vídeos', icon: 'pi pi-video', path: `${base}/videos`, exact: false },
      { label: 'Materiais', shortLabel: 'Materiais', icon: 'pi pi-file', path: `${base}/materiais`, exact: false },
      { label: 'Ranking', shortLabel: 'Ranking', icon: 'pi pi-chart-bar', path: `${base}/ranking`, exact: false },
      {
        label: 'Conquistas',
        shortLabel: 'Conquistas',
        icon: 'pi pi-star',
        path: `${base}/conquistas`,
        exact: false,
      },
    ];
  });

  /** Trocar de sala é uma navegação, não uma troca de estado. */
  protected onRoomChange(roomId: string): void {
    if (roomId && roomId !== this.roomId()) {
      void this.router.navigate(['/aluno/salas', roomId]);
    }
  }
}
