import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Paginator, type PaginatorState } from 'primeng/paginator';

import type { PageQuery } from '../../../../core/models/page';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { RankingService } from '../../data/ranking.service';

/**
 * Ranking da sala — Parte 4, §8.
 *
 * `displayName` já chega anonimizado pela API — primeiro nome e inicial do
 * sobrenome. O modelo `RankingEntry` sequer tem `email` ou `fullName`, então é
 * impossível esta tela vazar o nome completo de um colega.
 *
 * A linha do próprio aluno é destacada, e a posição dele aparece mesmo quando
 * está fora da página visível.
 */
@Component({
  selector: 'cc-ranking-page',
  imports: [
    Paginator,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ranking.html',
  styleUrl: './ranking.scss',
})
export class RankingPage {
  private readonly ranking = inject(RankingService);

  readonly roomId = input.required<string>();

  private readonly query = signal<PageQuery>({ page: 0, size: 20 });

  protected readonly state = createPageState(() =>
    this.ranking.ranking(this.roomId(), this.query()),
  );

  protected readonly first = computed(() => {
    const page = this.state.data();
    return page ? page.page * page.size : 0;
  });

  /** O próprio aluno está entre as linhas desta página? */
  protected readonly meVisible = computed(() => {
    const data = this.state.data();
    return data?.content.some((entry) => entry.me) ?? false;
  });

  constructor() {
    effect(() => {
      this.roomId();
      this.query();
      void this.state.load();
    });
  }

  protected onPage(event: PaginatorState): void {
    this.query.set({
      page: event.page ?? 0,
      size: event.rows ?? 20,
    });
  }

  /** Medalha para os três primeiros; posição numérica para o resto. */
  protected medal(position: number): string | null {
    switch (position) {
      case 1:
        return '🥇';
      case 2:
        return '🥈';
      case 3:
        return '🥉';
      default:
        return null;
    }
  }
}
