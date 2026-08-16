import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { Paginator, type PaginatorState } from 'primeng/paginator';
import { TableModule } from 'primeng/table';

import type { MediaViewType } from '../../../../core/models/enums';
import type { PageQuery } from '../../../../core/models/page';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { createPageState } from '../../../../shared/forms/page-state';
import { DateTimePipe } from '../../../../shared/pipes/format.pipes';
import { MediaAssignmentService } from '../../data/media-assignment.service';

/**
 * Visualizações de uma mídia por aluno — Parte 5, §8.
 *
 * `firstViewedAt` e `lastViewedAt` vêm da API; a abertura é idempotente por
 * aluno e mídia, então "visualizações" aqui é gente, não cliques.
 */
@Component({
  selector: 'cc-media-views-dialog',
  imports: [
    Dialog,
    Paginator,
    TableModule,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    DateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './media-views-dialog.html',
  styleUrl: './media-views-dialog.scss',
})
export class MediaViewsDialogComponent {
  private readonly service = inject(MediaAssignmentService);

  readonly mediaType = input.required<MediaViewType>();
  readonly mediaId = input.required<string>();
  readonly title = input('');

  readonly closed = output<void>();

  protected readonly visible = signal(true);
  protected readonly query = signal<PageQuery>({ page: 0, size: 20 });

  protected readonly state = createPageState(() =>
    this.service.views(this.mediaType(), this.mediaId(), this.query()),
  );

  constructor() {
    effect(() => {
      this.mediaId();
      this.query();
      void this.state.load();
    });
  }

  protected first(): number {
    const page = this.state.data();
    return page ? page.page * page.size : 0;
  }

  protected onPage(event: PaginatorState): void {
    this.query.set({ page: event.page ?? 0, size: event.rows ?? 20 });
  }

  protected onVisibleChange(visible: boolean): void {
    this.visible.set(visible);
    if (!visible) {
      this.closed.emit();
    }
  }
}
