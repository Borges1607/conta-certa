import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';

import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { EnumLabelPipe, RelativeTimePipe } from '../../../../shared/pipes/format.pipes';
import { TeacherDashboardService } from '../../data/teacher-dashboard.service';

/**
 * Painel do professor — Parte 5, §3.
 *
 * Todos os números vêm de `GET /teacher/dashboard`. O vazio de professor novo
 * tem ação contextual, como a §9 da spec de integração exige.
 */
@Component({
  selector: 'cc-teacher-dashboard-page',
  imports: [
    RouterLink,
    Button,
    Tag,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    EnumLabelPipe,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class TeacherDashboardPage {
  private readonly service = inject(TeacherDashboardService);
  private readonly router = inject(Router);

  protected readonly state = createPageState(() => this.service.load());

  constructor() {
    void this.state.load();
  }

  /** A sala nova é criada na própria lista de salas, pelo diálogo de lá. */
  protected goToRooms(): void {
    void this.router.navigate(['/professor/salas'], { queryParams: { nova: '1' } });
  }
}
