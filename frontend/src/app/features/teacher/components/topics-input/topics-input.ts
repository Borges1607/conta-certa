import { ChangeDetectionStrategy, Component, input, model, signal } from '@angular/core';
import { Button } from 'primeng/button';
import { Chip } from 'primeng/chip';
import { InputText } from 'primeng/inputtext';

/**
 * Lista ordenada de temas — Parte 5, §4.
 *
 * O `p-chips` do PrimeNG saiu na versão 21; este é o equivalente pedido pela
 * spec. Além de adicionar e remover, permite **reordenar**, porque a spec diz
 * que a lista é ordenada e a ordem é o que o aluno vê.
 */
@Component({
  selector: 'cc-topics-input',
  imports: [Button, Chip, InputText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './topics-input.html',
  styleUrl: './topics-input.scss',
})
export class TopicsInputComponent {
  readonly topics = model.required<string[]>();
  readonly disabled = input(false);
  readonly inputId = input('topics');
  readonly placeholder = input('Digite um tema e pressione Enter');

  protected readonly draft = signal('');

  protected add(): void {
    const value = this.draft().trim();
    if (!value || this.disabled()) {
      return;
    }

    const current = this.topics();
    if (current.some((topic) => topic.toLocaleLowerCase() === value.toLocaleLowerCase())) {
      this.draft.set('');
      return;
    }

    this.topics.set([...current, value]);
    this.draft.set('');
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.add();
    } else if (event.key === 'Backspace' && this.draft() === '') {
      this.removeLast();
    }
  }

  protected onInput(event: Event): void {
    this.draft.set((event.target as HTMLInputElement).value);
  }

  protected remove(index: number): void {
    if (this.disabled()) {
      return;
    }
    this.topics.set(this.topics().filter((_, i) => i !== index));
  }

  protected move(index: number, offset: number): void {
    const target = index + offset;
    const current = this.topics();
    if (this.disabled() || target < 0 || target >= current.length) {
      return;
    }

    const next = [...current];
    const [moved] = next.splice(index, 1);
    next.splice(target, 0, moved);
    this.topics.set(next);
  }

  private removeLast(): void {
    const current = this.topics();
    if (current.length > 0) {
      this.remove(current.length - 1);
    }
  }
}
