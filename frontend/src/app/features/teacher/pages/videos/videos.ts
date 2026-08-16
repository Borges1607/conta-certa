import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { Textarea } from 'primeng/textarea';

import { ApiError } from '../../../../core/api/problem-details';
import type { PageQuery } from '../../../../core/models/page';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import { VideoEmbedComponent } from '../../../../shared/components/video-embed/video-embed';
import {
  applyFieldErrors,
  autoClearServerErrors,
} from '../../../../shared/forms/apply-field-errors';
import { createPageState } from '../../../../shared/forms/page-state';
import { VersionConflictNoticeComponent } from '../../components/version-conflict-notice/version-conflict-notice';
import { VideoService } from '../../data/video.service';
import type { TeacherVideo } from '../../models/media';

/**
 * Acervo de videoaulas — Parte 5, §8.
 *
 * Vídeos são links externos. A URL é pré-visualizada antes de salvar pelo mesmo
 * `cc-video-embed` que o aluno usa: se não incorporar aqui, não vai incorporar
 * lá, e o professor descobre agora e não depois da aula.
 */
@Component({
  selector: 'cc-teacher-videos-page',
  imports: [
    ReactiveFormsModule,
    Button,
    Dialog,
    InputText,
    Textarea,
    TableModule,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    VideoEmbedComponent,
    VersionConflictNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './videos.html',
  styleUrl: './videos.scss',
})
export class TeacherVideosPage {
  private readonly fb = inject(FormBuilder);
  private readonly videos = inject(VideoService);
  private readonly notify = inject(NotificationService);

  protected readonly query = signal<PageQuery>({ page: 0, size: 50 });
  protected readonly state = createPageState(() => this.videos.list(this.query()));

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<TeacherVideo | null>(null);
  protected readonly conflict = signal(false);
  protected readonly busyId = signal<string | null>(null);

  protected readonly guard = createSubmitGuard();

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    description: [''],
    category: [''],
    url: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
  });

  /** URL digitada, para a pré-visualização acompanhar. */
  protected readonly previewUrl = signal('');

  constructor() {
    autoClearServerErrors(this.form);

    effect(() => {
      this.query();
      void this.state.load();
    });

    this.form.controls.url.valueChanges.subscribe((value) => this.previewUrl.set(value));
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.conflict.set(false);
    this.form.reset({ title: '', description: '', category: '', url: '' });
    this.previewUrl.set('');
    this.dialogVisible.set(true);
  }

  protected openEdit(video: TeacherVideo): void {
    this.editing.set(video);
    this.conflict.set(false);
    this.form.reset({
      title: video.title,
      description: video.description ?? '',
      category: video.category ?? '',
      url: video.url,
    });
    this.previewUrl.set(video.url);
    this.dialogVisible.set(true);
  }

  protected async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    const value = this.form.getRawValue();
    const current = this.editing();

    await this.guard.run(async () => {
      try {
        if (current) {
          await this.videos.update(current.id, {
            version: current.version,
            title: value.title,
            description: value.description || null,
            category: value.category || null,
            url: value.url,
          });
        } else {
          await this.videos.create({
            title: value.title,
            description: value.description || null,
            category: value.category || null,
            url: value.url,
          });
        }

        this.notify.success(current ? 'Videoaula atualizada' : 'Videoaula criada');
        this.dialogVisible.set(false);
        await this.state.refresh();
      } catch (error) {
        this.handleFailure(error);
      }
    });
  }

  protected async remove(video: TeacherVideo): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: `Arquivar "${video.title}"?`,
      message:
        'A videoaula sai do acervo ativo. Onde ela já estiver publicada, continua acessível aos alunos até você retirá-la da sala.',
      acceptLabel: 'Arquivar',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.busyId.set(video.id);
      try {
        await this.videos.remove(video.id);
        this.notify.success('Videoaula arquivada');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível arquivar.');
      } finally {
        this.busyId.set(null);
      }
    });
  }

  protected async reload(): Promise<void> {
    await this.state.refresh();
    this.conflict.set(false);
    this.dialogVisible.set(false);
    this.notify.info('Lista recarregada', 'Abra a videoaula de novo para editar a versão atual.');
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível salvar a videoaula.');
      return;
    }

    if (error.isVersionConflict) {
      this.conflict.set(true);
      return;
    }

    applyFieldErrors(this.form, error);
    if (error.fieldErrors.length === 0) {
      this.notify.error(error);
    }
  }
}
