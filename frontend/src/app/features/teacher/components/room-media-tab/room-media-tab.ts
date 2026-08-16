import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { Tag } from 'primeng/tag';

import { ApiError } from '../../../../core/api/problem-details';
import type { MediaViewType } from '../../../../core/models/enums';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { createPageState } from '../../../../shared/forms/page-state';
import { LessonAssignmentService } from '../../data/lesson-assignment.service';
import { MaterialService } from '../../data/material.service';
import { MediaAssignmentService } from '../../data/media-assignment.service';
import { VideoService } from '../../data/video.service';
import type { MediaAssignment } from '../../models/media';
import { MediaViewsDialogComponent } from '../media-views-dialog/media-views-dialog';

interface MediaOption {
  id: string;
  title: string;
}

interface LessonOption {
  id: string;
  title: string;
}

/**
 * Aba de mídias da sala — Parte 5, §8.
 *
 * Separa o que está vinculado a uma lição do que fica solto na sala, porque é
 * assim que o aluno vê: agrupado por lição, com um grupo "geral" no fim.
 */
@Component({
  selector: 'cc-room-media-tab',
  imports: [
    FormsModule,
    Button,
    Dialog,
    Select,
    Tag,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    MediaViewsDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-media-tab.html',
  styleUrl: './room-media-tab.scss',
})
export class RoomMediaTabComponent {
  private readonly mediaAssignments = inject(MediaAssignmentService);
  private readonly videos = inject(VideoService);
  private readonly materials = inject(MaterialService);
  private readonly assignments = inject(LessonAssignmentService);
  private readonly notify = inject(NotificationService);

  readonly roomId = input.required<string>();
  readonly archived = input(false);

  protected readonly state = createPageState(() => this.mediaAssignments.list(this.roomId()));

  protected readonly linked = computed(() =>
    (this.state.data() ?? []).filter((item) => item.lessonAssignmentId !== null),
  );
  protected readonly loose = computed(() =>
    (this.state.data() ?? []).filter((item) => item.lessonAssignmentId === null),
  );

  protected readonly publishVisible = signal(false);
  protected readonly mediaType = signal<MediaViewType>('VIDEO');
  protected readonly selectedMediaId = signal<string | null>(null);
  protected readonly selectedLessonAssignmentId = signal<string | null>(null);
  protected readonly mediaOptions = signal<MediaOption[]>([]);
  protected readonly lessonOptions = signal<LessonOption[]>([]);
  protected readonly optionsLoading = signal(false);
  protected readonly busyId = signal<string | null>(null);

  /** Visualizações abertas no diálogo. */
  protected readonly viewsTarget = signal<MediaAssignment | null>(null);

  protected readonly guard = createSubmitGuard();

  protected readonly typeOptions = [
    { value: 'VIDEO' as const, label: 'Videoaula' },
    { value: 'MATERIAL' as const, label: 'Material' },
  ];

  constructor() {
    effect(() => {
      this.roomId();
      void this.state.load();
    });
  }

  protected async openPublish(): Promise<void> {
    this.selectedMediaId.set(null);
    this.selectedLessonAssignmentId.set(null);
    this.publishVisible.set(true);
    await this.loadOptions();
  }

  protected async onTypeChange(type: MediaViewType): Promise<void> {
    this.mediaType.set(type);
    this.selectedMediaId.set(null);
    await this.loadOptions();
  }

  protected async publish(): Promise<void> {
    const mediaId = this.selectedMediaId();
    if (!mediaId) {
      return;
    }

    await this.guard.run(async () => {
      try {
        await this.mediaAssignments.create(this.roomId(), {
          mediaType: this.mediaType(),
          mediaId,
          lessonAssignmentId: this.selectedLessonAssignmentId(),
        });
        this.notify.success('Mídia publicada na sala');
        this.publishVisible.set(false);
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível publicar.');
      }
    });
  }

  protected async remove(assignment: MediaAssignment): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Retirar "${assignment.title}" da sala?`,
      message:
        'A mídia continua no seu acervo e pode ser publicada de novo. As visualizações já registradas são preservadas.',
      acceptLabel: 'Retirar da sala',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.busyId.set(assignment.id);
      try {
        await this.mediaAssignments.remove(this.roomId(), assignment.id);
        this.notify.success('Mídia retirada da sala');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível retirar a mídia.');
      } finally {
        this.busyId.set(null);
      }
    });
  }

  private async loadOptions(): Promise<void> {
    this.optionsLoading.set(true);
    try {
      const [media, track] = await Promise.all([
        this.mediaType() === 'VIDEO'
          ? this.videos.options().then((list) => list.map((v) => ({ id: v.id, title: v.title })))
          : this.materials.options().then((list) => list.map((m) => ({ id: m.id, title: m.title }))),
        this.assignments.list(this.roomId()),
      ]);

      this.mediaOptions.set(media);
      this.lessonOptions.set(track.map((item) => ({ id: item.id, title: item.lesson.title })));
    } catch (error) {
      this.notify.error(
        error instanceof ApiError ? error : 'Não foi possível carregar o acervo.',
      );
    } finally {
      this.optionsLoading.set(false);
    }
  }
}
