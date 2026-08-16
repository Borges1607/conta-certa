import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';

import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { AchievementCardComponent } from '../../components/achievement-card/achievement-card';
import { AchievementService } from '../../data/achievement.service';

/**
 * Conquistas da sala — Parte 4, §8.
 *
 * Cada conquista é calculada por sala pela API. Esta tela não compara XP nem
 * conta lições: ela separa desbloqueadas de bloqueadas e apresenta.
 */
@Component({
  selector: 'cc-achievements-page',
  imports: [
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    AchievementCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './achievements.html',
  styleUrl: './achievements.scss',
})
export class AchievementsPage {
  private readonly achievements = inject(AchievementService);

  readonly roomId = input.required<string>();

  protected readonly state = createPageState(() => this.achievements.achievements(this.roomId()));

  protected readonly unlocked = computed(
    () => this.state.data()?.filter((item) => item.unlocked) ?? [],
  );

  protected readonly locked = computed(
    () => this.state.data()?.filter((item) => !item.unlocked) ?? [],
  );

  constructor() {
    effect(() => {
      this.roomId();
      void this.state.load();
    });
  }
}
