import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { Tag } from 'primeng/tag';

import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { SecureFileLinkComponent } from '../../../../shared/components/secure-file-link/secure-file-link';
import { createPageState } from '../../../../shared/forms/page-state';
import { FileSizePipe } from '../../../../shared/pipes/format.pipes';
import { StudentMediaService } from '../../data/student-media.service';
import { groupByLesson } from '../../models/group-media';
import type { StudentMaterial } from '../../models/media';

/**
 * Materiais da sala — Parte 4, §7.
 *
 * Arquivos privados são abertos **exclusivamente** por `cc-secure-file-link`,
 * que baixa o blob autorizado e revoga a URL depois. Nenhuma URL da API vai
 * para `href` (§11 da spec de integração).
 */
@Component({
  selector: 'cc-student-materials-page',
  imports: [
    Tag,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    SecureFileLinkComponent,
    FileSizePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './materials.html',
  styleUrl: './materials.scss',
})
export class StudentMaterialsPage {
  private readonly media = inject(StudentMediaService);

  readonly roomId = input.required<string>();

  protected readonly state = createPageState(() => this.media.materials(this.roomId()));

  protected readonly groups = computed(() => groupByLesson(this.state.data()?.items ?? []));

  constructor() {
    effect(() => {
      this.roomId();
      void this.state.load();
    });
  }

  /** A abertura não espera o registro — ele é telemetria, não permissão. */
  protected async register(material: StudentMaterial): Promise<void> {
    await this.media.registerView('MATERIAL', material.id);
    await this.state.refresh();
  }
}
