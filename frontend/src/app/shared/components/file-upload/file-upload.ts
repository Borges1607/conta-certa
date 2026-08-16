import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { Button } from 'primeng/button';
import { ProgressBar } from 'primeng/progressbar';

import { formatFileSize } from '../../../core/util/format';

/** Perfis de arquivo aceitos — §7.4 da spec de integração. */
export const MATERIAL_ACCEPT = {
  mimeTypes: [
    'application/pdf',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  ],
  extensions: ['.pdf', '.ppt', '.pptx'],
  maxBytes: 10 * 1024 * 1024,
  description: 'PDF, PPT ou PPTX de até 10 MB',
} as const;

export const IMAGE_ACCEPT = {
  mimeTypes: ['image/png', 'image/jpeg', 'image/webp'],
  extensions: ['.png', '.jpg', '.jpeg', '.webp'],
  maxBytes: 5 * 1024 * 1024,
  description: 'PNG, JPEG ou WebP de até 5 MB',
} as const;

export interface FileAccept {
  readonly mimeTypes: readonly string[];
  readonly extensions: readonly string[];
  readonly maxBytes: number;
  readonly description: string;
}

/**
 * Seleção e envio de arquivo — Parte 2, §4.3.
 *
 * Valida tipo e tamanho **antes** de enviar, mostra progresso real e trata
 * `413`/`415` sem perder o arquivo selecionado, conforme a §9 da spec de
 * integração. O envio em si é do componente pai, que conhece o endpoint; aqui
 * ficam a seleção, a validação e a apresentação.
 */
@Component({
  selector: 'cc-file-upload',
  imports: [Button, ProgressBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="upload">
      <input
        #input
        type="file"
        class="cc-sr-only"
        [accept]="acceptAttribute()"
        [disabled]="uploading()"
        (change)="onSelect($event)"
      />

      @if (!selected()) {
        <div class="upload__dropzone" [class.upload__dropzone--over]="dragOver()"
             (dragover)="onDragOver($event)"
             (dragleave)="dragOver.set(false)"
             (drop)="onDrop($event)">
          <i class="pi pi-cloud-upload" aria-hidden="true"></i>
          <p class="upload__hint">Arraste um arquivo ou</p>
          <p-button
            label="Escolher arquivo"
            icon="pi pi-paperclip"
            [outlined]="true"
            [disabled]="uploading()"
            (onClick)="input.click()"
          />
          <small class="cc-muted">{{ accept().description }}</small>
        </div>
      } @else {
        <div class="upload__file">
          <i class="pi pi-file" aria-hidden="true"></i>
          <div class="upload__meta">
            <span class="upload__name cc-truncate">{{ selected()!.name }}</span>
            <small class="cc-muted">{{ sizeLabel() }}</small>
          </div>
          @if (!uploading()) {
            <p-button
              icon="pi pi-times"
              severity="secondary"
              [text]="true"
              ariaLabel="Remover arquivo"
              (onClick)="clear()"
            />
          }
        </div>

        @if (uploading()) {
          <div class="upload__progress">
            <p-progressbar [value]="progress() ?? 0" [showValue]="progress() !== null" />
            <p-button
              label="Cancelar envio"
              severity="secondary"
              [text]="true"
              size="small"
              (onClick)="cancelled.emit()"
            />
          </div>
        }
      }

      @if (localError()) {
        <small class="upload__error" role="alert">{{ localError() }}</small>
      }
    </div>
  `,
  styleUrl: './file-upload.scss',
})
export class FileUploadComponent {
  readonly accept = input<FileAccept>(MATERIAL_ACCEPT);
  /** Percentual 0–100 durante o envio; `null` quando o total é desconhecido. */
  readonly progress = input<number | null>(null);
  readonly uploading = input(false);

  readonly fileSelected = output<File>();
  readonly fileCleared = output<void>();
  readonly cancelled = output<void>();

  protected readonly selected = signal<File | null>(null);
  protected readonly localError = signal('');
  protected readonly dragOver = signal(false);

  protected readonly sizeLabel = computed(() => {
    const file = this.selected();
    return file ? formatFileSize(file.size) : '';
  });

  protected readonly acceptAttribute = computed(() =>
    [...this.accept().mimeTypes, ...this.accept().extensions].join(','),
  );

  protected onSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.take(file);
    }
    // Permite reescolher o mesmo arquivo depois de um erro.
    input.value = '';
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (!this.uploading()) {
      this.dragOver.set(true);
    }
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    if (this.uploading()) {
      return;
    }
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.take(file);
    }
  }

  protected clear(): void {
    this.selected.set(null);
    this.localError.set('');
    this.fileCleared.emit();
  }

  /**
   * Mantém o arquivo selecionado depois de um `413`/`415` do servidor, para o
   * usuário corrigir sem reescolher.
   */
  showServerError(message: string): void {
    this.localError.set(message);
  }

  private take(file: File): void {
    const error = this.validate(file);
    if (error) {
      this.localError.set(error);
      this.selected.set(null);
      return;
    }

    this.localError.set('');
    this.selected.set(file);
    this.fileSelected.emit(file);
  }

  private validate(file: File): string | null {
    const accept = this.accept();

    if (file.size > accept.maxBytes) {
      return `Arquivo de ${formatFileSize(file.size)}. O limite é ${formatFileSize(accept.maxBytes)}.`;
    }

    const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase();
    const typeOk =
      accept.mimeTypes.includes(file.type) || accept.extensions.includes(extension);

    return typeOk ? null : `Tipo não aceito. Envie ${accept.description}.`;
  }
}
