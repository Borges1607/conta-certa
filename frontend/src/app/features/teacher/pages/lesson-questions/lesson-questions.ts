import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';

import { ApiError } from '../../../../core/api/problem-details';
import { QUESTION_TYPE_LABELS } from '../../../../core/models/labels';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import { PageHeaderComponent, type Crumb } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { QuestionEditorComponent } from '../../components/question-editor/question-editor';
import { LessonService } from '../../data/lesson.service';
import { QuestionService } from '../../data/question.service';
import type { Question } from '../../models/question';

/**
 * Questões de uma lição — Parte 5, §6.3.
 *
 * A reordenação é otimista e **revertida se a API recusar**. Excluir pode
 * resultar em arquivamento lógico quando a questão já foi respondida: a tela
 * reflete o que a resposta indicar, em vez de supor.
 */
@Component({
  selector: 'cc-lesson-questions-page',
  imports: [
    RouterLink,
    Button,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    MarkdownComponent,
    QuestionEditorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lesson-questions.html',
  styleUrl: './lesson-questions.scss',
})
export class LessonQuestionsPage {
  private readonly questions = inject(QuestionService);
  private readonly lessons = inject(LessonService);
  private readonly notify = inject(NotificationService);

  readonly lessonId = input.required<string>();

  protected readonly typeLabels = QUESTION_TYPE_LABELS;

  protected readonly lesson = createPageState(() => this.lessons.get(this.lessonId()));
  protected readonly state = createPageState(() => this.questions.list(this.lessonId()));

  protected readonly editorVisible = signal(false);
  protected readonly editing = signal<Question | null>(null);
  protected readonly busyId = signal<string | null>(null);

  private readonly guard = createSubmitGuard();

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Lições', link: '/professor/licoes' },
    { label: this.lesson.data()?.title ?? 'Lição', link: `/professor/licoes/${this.lessonId()}` },
    { label: 'Questões' },
  ]);

  constructor() {
    effect(() => {
      this.lessonId();
      void this.lesson.load();
      void this.state.load();
    });
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.editorVisible.set(true);
  }

  protected openEdit(question: Question): void {
    this.editing.set(question);
    this.editorVisible.set(true);
  }

  protected async onSaved(): Promise<void> {
    await this.state.refresh();
    await this.lesson.refresh();
  }

  protected async move(question: Question, direction: -1 | 1): Promise<void> {
    const current = this.state.data();
    if (!current) {
      return;
    }

    const index = current.findIndex((item) => item.id === question.id);
    const target = index + direction;
    if (index === -1 || target < 0 || target >= current.length) {
      return;
    }

    const reordered = [...current];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    this.state.set(reordered);

    await this.guard.run(async () => {
      try {
        const saved = await this.questions.reorder(this.lessonId(), {
          questionIds: reordered.map((item) => item.id),
        });
        this.state.set(saved);
      } catch (error) {
        this.state.set(current);
        this.notify.error(
          error instanceof ApiError ? error : 'Não foi possível reordenar as questões.',
          'Ordem não salva',
        );
      }
    });
  }

  protected async remove(question: Question): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: 'Excluir esta questão?',
      message:
        'Se a questão já foi respondida por algum aluno, ela será arquivada em vez de removida — as tentativas antigas precisam continuar íntegras.',
      acceptLabel: 'Excluir',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.busyId.set(question.id);
      try {
        const result = await this.questions.remove(question.id);
        // A API decide entre remover e arquivar; a mensagem segue a resposta.
        this.notify.success(
          result.archived ? 'Questão arquivada' : 'Questão excluída',
          result.archived
            ? 'Ela já havia sido respondida, então foi arquivada para preservar as tentativas.'
            : undefined,
        );
        await this.onSaved();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível excluir.');
      } finally {
        this.busyId.set(null);
      }
    });
  }

  /** Resumo curto da resposta certa, para o professor conferir de relance. */
  protected answerSummary(question: Question): string {
    switch (question.type) {
      case 'SINGLE_CHOICE':
      case 'MULTIPLE_CHOICE': {
        const correct = question.options.filter((option) => option.correct);
        return correct.map((option) => option.text).join(' · ') || 'Sem gabarito definido';
      }
      case 'TRUE_FALSE':
        return question.correctBoolean === null
          ? 'Sem gabarito definido'
          : question.correctBoolean
            ? 'Verdadeiro'
            : 'Falso';
      case 'NUMERIC': {
        const value = question.correctNumericValue ?? '—';
        const tolerance = question.absoluteTolerance;
        return tolerance && tolerance !== '0' ? `${value} (± ${tolerance})` : value;
      }
    }
  }
}
