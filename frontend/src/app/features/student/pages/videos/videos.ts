import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Tag } from 'primeng/tag';

import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { VideoEmbedComponent } from '../../../../shared/components/video-embed/video-embed';
import { createPageState } from '../../../../shared/forms/page-state';
import { StudentMediaService } from '../../data/student-media.service';
import { groupByLesson } from '../../models/group-media';
import type { StudentVideo } from '../../models/media';

/**
 * Videoaulas da sala — Parte 4, §7.
 *
 * Abrir um vídeo registra a visualização. O registro é idempotente e **não
 * bloqueia a abertura**: se a chamada falhar, o aluno assiste do mesmo jeito.
 */
@Component({
  selector: 'cc-student-videos-page',
  imports: [
    Button,
    Dialog,
    Tag,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    VideoEmbedComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './videos.html',
  styleUrl: './videos.scss',
})
export class StudentVideosPage {
  private readonly media = inject(StudentMediaService);

  readonly roomId = input.required<string>();

  protected readonly state = createPageState(() => this.media.videos(this.roomId()));

  protected readonly groups = computed(() => groupByLesson(this.state.data()?.items ?? []));

  protected readonly playing = signal<StudentVideo | null>(null);

  constructor() {
    effect(() => {
      this.roomId();
      void this.state.load();
    });
  }

  protected async open(video: StudentVideo): Promise<void> {
    this.playing.set(video);
    // Não aguardamos: a abertura não depende do registro.
    void this.media.registerView('VIDEO', video.id).then(() => this.state.refresh());
  }
}
