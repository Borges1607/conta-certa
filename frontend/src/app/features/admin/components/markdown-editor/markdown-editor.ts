import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Textarea } from 'primeng/textarea';

import { MarkdownComponent } from '../../../../shared/components/markdown/markdown';

/**
 * Editor de Markdown com pré-visualização — Parte 6, §6.
 *
 * A pré-visualização usa `cc-markdown`, o mesmo componente que renderiza a dica
 * no painel do aluno. Isso é intencional: o conteúdo passa exatamente pelo
 * mesmo caminho de sanitização (DOMPurify + KaTeX) na edição e na leitura, sem
 * um segundo renderizador para manter em dia.
 *
 * A Parte 5 prevê o mesmo editor para o professor. Quando ela existir, este
 * componente deve subir para `shared/components/` sem alteração de API.
 */
@Component({
  selector: 'cc-markdown-editor',
  imports: [Textarea, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MarkdownEditorComponent),
      multi: true,
    },
  ],
  templateUrl: './markdown-editor.html',
  styleUrl: './markdown-editor.scss',
})
export class MarkdownEditorComponent implements ControlValueAccessor {
  readonly inputId = input('markdown');
  readonly rows = input(10);
  readonly placeholder = input('Escreva em Markdown. Fórmulas em KaTeX são aceitas.');

  protected readonly value = signal('');
  protected readonly isDisabled = signal(false);
  protected readonly mode = signal<'write' | 'preview'>('write');

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.isDisabled.set(isDisabled);
  }

  protected setMode(mode: 'write' | 'preview'): void {
    this.mode.set(mode);
  }

  protected onInput(event: Event): void {
    const text = (event.target as HTMLTextAreaElement).value;
    this.value.set(text);
    this.onChange(text);
  }

  protected onBlur(): void {
    this.onTouched();
  }
}
