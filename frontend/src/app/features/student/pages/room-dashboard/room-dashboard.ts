import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';

import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { ProgressBarComponent } from '../../../../shared/components/progress-bar/progress-bar';
import { LocalDatePipe } from '../../../../shared/pipes/format.pipes';
import { AchievementCardComponent } from '../../components/achievement-card/achievement-card';
import { AttemptLauncher } from '../../data/attempt-launcher';
import { RoomContextStore } from '../../data/room-context.store';

/**
 * Visão geral da sala — Parte 4, §4.
 *
 * `GET /student/rooms/{roomId}/dashboard` é a **fonte única** desta tela. Todo
 * número apresentado aqui vem pronto da API: progresso, nível, XP, estrelas,
 * lições concluídas e posição no ranking. Não há uma única conta neste arquivo,
 * e é assim que o critério "o frontend não recalcula resultados oficiais" se
 * cumpre (§11 da spec de integração).
 */
@Component({
  selector: 'cc-room-dashboard-page',
  imports: [
    RouterLink,
    Button,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    ProgressBarComponent,
    MarkdownComponent,
    AchievementCardComponent,
    LocalDatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-dashboard.html',
  styleUrl: './room-dashboard.scss',
})
export class RoomDashboardPage {
  private readonly launcher = inject(AttemptLauncher);

  protected readonly context = inject(RoomContextStore);
  protected readonly state = this.context.dashboard;
  protected readonly starting = this.launcher.starting;

  /**
   * "Continuar de onde parou".
   *
   * Quando há tentativa em andamento, retomamos; caso contrário a trilha é o
   * caminho, porque é lá que as regras da lição são mostradas antes de começar.
   */
  protected async resume(activeAttemptId: string | null): Promise<void> {
    if (activeAttemptId) {
      await this.launcher.resume(activeAttemptId);
    }
  }
}
