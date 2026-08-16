import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { FormFieldComponent } from '../../../../shared/components/form-field/form-field';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent, type Crumb } from '../../../../shared/components/page-header/page-header';
import { SubmitButtonComponent } from '../../../../shared/components/submit-button/submit-button';
import {
  applyFieldErrors,
  autoClearServerErrors,
} from '../../../../shared/forms/apply-field-errors';
import { createPageState } from '../../../../shared/forms/page-state';
import { LessonStatusTagComponent } from '../../components/lesson-status-tag/lesson-status-tag';
import { MarkdownEditorComponent } from '../../components/markdown-editor/markdown-editor';
import { VersionConflictNoticeComponent } from '../../components/version-conflict-notice/version-conflict-notice';
import { LessonService } from '../../data/lesson.service';

/**
 * Editor de lição — Parte 5, §6.2.
 *
 * `version` acompanha toda alteração. Em `409 VERSION_CONFLICT` a tela oferece
 * **Recarregar** e nada mais: salvar por cima é exatamente o que o critério de
 * aceite proíbe.
 *
 * O aviso de alterações não salvas existe porque o salvamento é explícito —
 * perder uma teoria longa por um clique no menu seria caro demais.
 */
@Component({
  selector: 'cc-lesson-editor-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    InputText,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    FormFieldComponent,
    SubmitButtonComponent,
    MarkdownEditorComponent,
    LessonStatusTagComponent,
    VersionConflictNoticeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lesson-editor.html',
  styleUrl: './lesson-editor.scss',
})
export class LessonEditorPage {
  private readonly fb = inject(FormBuilder);
  private readonly lessons = inject(LessonService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly lessonId = input.required<string>();

  protected readonly guard = createSubmitGuard();
  protected readonly publishGuard = createSubmitGuard();
  protected readonly conflict = signal(false);
  protected readonly reloading = signal(false);
  protected readonly theory = signal('');
  protected readonly dirty = signal(false);

  protected readonly state = createPageState(() => this.lessons.get(this.lessonId()));

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    summary: [''],
  });

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Lições', link: '/professor/licoes' },
    { label: this.state.data()?.title ?? 'Lição' },
  ]);

  constructor() {
    autoClearServerErrors(this.form);

    effect(() => {
      this.lessonId();
      void this.load();
    });

    // Qualquer edição marca o rascunho como não salvo.
    this.form.valueChanges.subscribe(() => this.dirty.set(true));
  }

  protected onTheoryChange(value: string): void {
    this.theory.set(value);
    this.dirty.set(true);
  }

  protected async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    const current = this.state.data();
    if (!current) {
      return;
    }

    await this.guard.run(async () => {
      try {
        const saved = await this.lessons.update(this.lessonId(), {
          version: current.version,
          title: this.form.controls.title.value,
          summary: this.form.controls.summary.value || null,
          theoryMarkdown: this.theory(),
        });
        this.state.set(saved);
        this.dirty.set(false);
        this.conflict.set(false);
        this.notify.success('Lição salva');
      } catch (error) {
        this.handleFailure(error);
      }
    });
  }

  protected async publish(): Promise<void> {
    if (this.dirty()) {
      const proceed = await this.notify.confirm({
        header: 'Salvar antes de publicar?',
        message: 'Você tem alterações não salvas. Elas serão salvas antes da publicação.',
        acceptLabel: 'Salvar e publicar',
      });
      if (!proceed) {
        return;
      }
      await this.save();
      if (this.dirty()) {
        return;
      }
    }

    await this.publishGuard.run(async () => {
      try {
        const published = await this.lessons.publish(this.lessonId());
        this.state.set(published);
        this.notify.success('Lição publicada', 'Agora ela pode ser atribuída às suas salas.');
      } catch (error) {
        if (error instanceof ApiError && error.status === 422) {
          this.notify.warn('Não foi possível publicar', error.detail);
          return;
        }
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível publicar.');
      }
    });
  }

  /** Recarrega após conflito de versão — nunca sobrescreve. */
  protected async reload(): Promise<void> {
    this.reloading.set(true);
    try {
      await this.load();
      this.conflict.set(false);
      this.notify.info('Conteúdo recarregado', 'O editor mostra a versão mais recente.');
    } finally {
      this.reloading.set(false);
    }
  }

  protected async goToQuestions(): Promise<void> {
    await this.router.navigate(['/professor/licoes', this.lessonId(), 'questoes']);
  }

  private async load(): Promise<void> {
    await this.state.load();
    const lesson = this.state.data();
    if (lesson) {
      this.form.setValue({ title: lesson.title, summary: lesson.summary ?? '' });
      this.theory.set(lesson.theoryMarkdown);
      this.dirty.set(false);
    }
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível salvar a lição.');
      return;
    }

    if (error.isVersionConflict) {
      this.conflict.set(true);
      return;
    }

    // O formulário é preservado: o professor corrige sem reescrever.
    applyFieldErrors(this.form, error);
    if (error.fieldErrors.length === 0) {
      this.notify.error(error);
    }
  }
}
