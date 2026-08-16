import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Menu } from 'primeng/menu';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';

import { ApiError } from '../../../../core/api/problem-details';
import type { ContentStatus } from '../../../../core/models/enums';
import { CONTENT_STATUS_LABELS } from '../../../../core/models/labels';
import type { PageQuery } from '../../../../core/models/page';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { RelativeTimePipe } from '../../../../shared/pipes/format.pipes';
import { LessonStatusTagComponent } from '../../components/lesson-status-tag/lesson-status-tag';
import { LessonService } from '../../data/lesson.service';
import type { LessonSummary } from '../../models/lesson';

/**
 * Acervo de lições — Parte 5, §6.1.
 *
 * A lição pertence ao **professor**, não a uma sala: a mesma lição pode estar
 * na trilha de várias turmas. Por isso a coluna "salas" existe — publicar ou
 * arquivar aqui afeta todas elas, e o professor precisa ver isso antes.
 */
@Component({
  selector: 'cc-lessons-page',
  imports: [
    FormsModule,
    RouterLink,
    Button,
    InputText,
    Select,
    Menu,
    TableModule,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    LessonStatusTagComponent,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lessons.html',
  styleUrl: './lessons.scss',
})
export class LessonsPage {
  private readonly lessons = inject(LessonService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly search = signal('');
  protected readonly status = signal<ContentStatus | null>(null);
  protected readonly query = signal<PageQuery>({ page: 0, size: 50 });
  protected readonly busyId = signal<string | null>(null);

  protected readonly guard = createSubmitGuard();

  protected readonly statusOptions = [
    { value: null, label: 'Todas as situações' },
    ...(Object.keys(CONTENT_STATUS_LABELS) as ContentStatus[]).map((value) => ({
      value,
      label: CONTENT_STATUS_LABELS[value],
    })),
  ];

  protected readonly state = createPageState(() =>
    this.lessons.list(this.query(), this.status(), this.search()),
  );

  constructor() {
    effect(() => {
      this.query();
      this.status();
      this.search();
      void this.state.load();
    });
  }

  protected menuFor(lesson: LessonSummary): MenuItem[] {
    const items: MenuItem[] = [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => void this.router.navigate(['/professor/licoes', lesson.id]),
      },
      {
        label: 'Questões',
        icon: 'pi pi-list',
        command: () => void this.router.navigate(['/professor/licoes', lesson.id, 'questoes']),
      },
      {
        label: 'Duplicar',
        icon: 'pi pi-copy',
        command: () => void this.duplicate(lesson),
      },
    ];

    if (lesson.status === 'DRAFT') {
      items.unshift({
        label: 'Publicar',
        icon: 'pi pi-send',
        command: () => void this.publish(lesson),
      });
    }

    if (lesson.status !== 'ARCHIVED') {
      items.push(
        { separator: true },
        { label: 'Arquivar', icon: 'pi pi-inbox', command: () => void this.archive(lesson) },
      );
    }

    return items;
  }

  protected async create(): Promise<void> {
    await this.guard.run(async () => {
      try {
        const lesson = await this.lessons.create({
          title: 'Nova lição',
          summary: null,
          theoryMarkdown: '## Conteúdo\n\nEscreva aqui a teoria da lição.',
        });
        this.notify.success('Rascunho criado', 'Escreva o conteúdo e adicione as questões.');
        await this.router.navigate(['/professor/licoes', lesson.id]);
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível criar a lição.');
      }
    });
  }

  protected async publish(lesson: LessonSummary): Promise<void> {
    await this.run(lesson.id, () => this.lessons.publish(lesson.id), 'Lição publicada');
  }

  protected async archive(lesson: LessonSummary): Promise<void> {
    const message =
      lesson.assignmentCount > 0
        ? `Esta lição está em ${lesson.assignmentCount} ${lesson.assignmentCount === 1 ? 'sala' : 'salas'}. Arquivar impede novas atribuições; as trilhas existentes continuam como estão.`
        : 'A lição sai do acervo ativo. Você pode consultá-la depois pelo filtro de arquivadas.';

    const confirmed = await this.notify.destructive({
      header: `Arquivar "${lesson.title}"?`,
      message,
      acceptLabel: 'Arquivar',
    });

    if (confirmed) {
      await this.run(lesson.id, () => this.lessons.archive(lesson.id), 'Lição arquivada');
    }
  }

  protected async duplicate(lesson: LessonSummary): Promise<void> {
    await this.run(
      lesson.id,
      () => this.lessons.duplicate(lesson.id),
      'Lição duplicada, com as questões',
    );
  }

  protected applySearch(value: string): void {
    this.search.set(value);
    this.query.set({ page: 0, size: 50 });
  }

  private async run(id: string, action: () => Promise<unknown>, success: string): Promise<void> {
    await this.guard.run(async () => {
      this.busyId.set(id);
      try {
        await action();
        this.notify.success(success);
        await this.state.refresh();
      } catch (error) {
        this.handleFailure(error);
      } finally {
        this.busyId.set(null);
      }
    });
  }

  private handleFailure(error: unknown): void {
    if (error instanceof ApiError && error.status === 422) {
      // Publicação recusada normalmente é falta de questões — a mensagem do
      // servidor explica melhor que qualquer texto genérico nosso.
      this.notify.warn('Não foi possível publicar', error.detail);
      return;
    }
    this.notify.error(error instanceof ApiError ? error : 'Não foi possível concluir a ação.');
  }
}
