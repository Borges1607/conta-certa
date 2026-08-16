import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  model,
  signal,
  viewChild,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import {
  FileUploadComponent,
  IMAGE_ACCEPT,
} from '../../../../shared/components/file-upload/file-upload';
import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';
import { LessonService } from '../../data/lesson.service';

interface ToolbarAction {
  id: string;
  label: string;
  icon: string;
  before: string;
  after: string;
  placeholder: string;
  /** Insere em linha nova em vez de envolver a seleção. */
  block?: boolean;
}

/**
 * Editor de Markdown com pré-visualização ao vivo — Parte 5, §6.2.
 *
 * A pré-visualização usa o **mesmo** `cc-markdown` do aluno, com a mesma
 * sanitização e o mesmo KaTeX. É esse compartilhamento — e não um renderizador
 * paralelo — que garante que o professor veja exatamente o que o aluno verá.
 */
@Component({
  selector: 'cc-markdown-editor',
  imports: [Button, Dialog, Textarea, FileUploadComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './markdown-editor.html',
  styleUrl: './markdown-editor.scss',
})
export class MarkdownEditorComponent {
  private readonly lessons = inject(LessonService);
  private readonly notify = inject(NotificationService);

  readonly value = model.required<string>();
  readonly disabled = input(false);
  readonly rows = input(18);
  readonly editorId = input('markdown-source');
  /** Sem lição ainda salva não há para onde enviar imagem. */
  readonly lessonId = input<string | null>(null);

  protected readonly imageAccept = IMAGE_ACCEPT;
  protected readonly uploadVisible = signal(false);
  protected readonly uploading = signal(false);
  protected readonly uploadProgress = signal<number | null>(null);

  protected readonly toolbar: ToolbarAction[] = [
    { id: 'h2', label: 'Título', icon: 'pi pi-hashtag', before: '## ', after: '', placeholder: 'Título', block: true },
    { id: 'bold', label: 'Negrito', icon: 'pi pi-bold', before: '**', after: '**', placeholder: 'texto' },
    { id: 'list', label: 'Lista', icon: 'pi pi-list', before: '- ', after: '', placeholder: 'item', block: true },
    {
      id: 'table',
      label: 'Tabela',
      icon: 'pi pi-table',
      before: '| Coluna A | Coluna B |\n| --- | --- |\n| ',
      after: ' |  |',
      placeholder: 'valor',
      block: true,
    },
    { id: 'code', label: 'Código', icon: 'pi pi-code', before: '```\n', after: '\n```', placeholder: 'código', block: true },
    { id: 'math', label: 'Fórmula', icon: 'pi pi-percentage', before: '$', after: '$', placeholder: 'M=C(1+i)^t' },
  ];

  private readonly textarea = viewChild.required<ElementRef<HTMLTextAreaElement>>('source');
  private readonly upload = viewChild(FileUploadComponent);
  private uploadSubscription: Subscription | null = null;

  protected onInput(event: Event): void {
    this.value.set((event.target as HTMLTextAreaElement).value);
  }

  protected apply(action: ToolbarAction): void {
    if (this.disabled()) {
      return;
    }

    const element = this.textarea().nativeElement;
    const start = element.selectionStart;
    const end = element.selectionEnd;
    const text = this.value();
    const selected = text.slice(start, end) || action.placeholder;

    const needsBreak = action.block && start > 0 && text[start - 1] !== '\n';
    const prefix = `${needsBreak ? '\n' : ''}${action.before}`;
    const inserted = `${prefix}${selected}${action.after}`;

    this.value.set(text.slice(0, start) + inserted + text.slice(end));

    // Devolve o cursor cercando o trecho inserido, para continuar digitando.
    queueMicrotask(() => {
      const from = start + prefix.length;
      element.focus();
      element.setSelectionRange(from, from + selected.length);
    });
  }

  protected openUpload(): void {
    if (!this.lessonId()) {
      this.notify.info(
        'Salve a lição primeiro',
        'A imagem é anexada à lição, então ela precisa existir antes do envio.',
      );
      return;
    }
    this.uploadVisible.set(true);
  }

  protected closeUpload(): void {
    this.cancelUpload();
    this.uploadVisible.set(false);
  }

  protected onFileSelected(file: File): void {
    const lessonId = this.lessonId();
    if (!lessonId || this.uploading()) {
      return;
    }

    this.uploading.set(true);
    this.uploadProgress.set(0);

    this.uploadSubscription = this.lessons.uploadImage(lessonId, file).subscribe({
      next: (event) => {
        if (event.kind === 'progress') {
          this.uploadProgress.set(event.percent);
          return;
        }
        this.insertImage(event.body.url, event.body.fileName);
        this.uploading.set(false);
        this.uploadVisible.set(false);
        this.notify.success('Imagem enviada', 'A referência foi inserida no texto.');
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.uploadProgress.set(null);
        this.showUploadError(error);
      },
    });
  }

  protected cancelUpload(): void {
    this.uploadSubscription?.unsubscribe();
    this.uploadSubscription = null;
    this.uploading.set(false);
    this.uploadProgress.set(null);
  }

  /** Insere a referência retornada pela API no ponto do cursor. */
  private insertImage(url: string, fileName: string): void {
    const element = this.textarea().nativeElement;
    const start = element.selectionStart;
    const text = this.value();
    const snippet = `\n![${fileName}](${url})\n`;
    this.value.set(text.slice(0, start) + snippet + text.slice(start));
  }

  /**
   * `413` e `415` mantêm o arquivo selecionado, com a mensagem junto do campo —
   * §9 da spec de integração.
   */
  private showUploadError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.notify.error('Não foi possível enviar a imagem.');
      return;
    }

    if (error.status === 413 || error.status === 415) {
      this.upload()?.showServerError(error.detail);
      return;
    }

    this.notify.error(error, 'Não foi possível enviar a imagem');
  }
}
