import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { InputText } from 'primeng/inputtext';

import { formatCnpj, normalizeCnpj } from '../../util/cnpj';

/**
 * Entrada de CNPJ — Parte 6, §4.
 *
 * O ponto central: **o valor do controle é sempre o normalizado**, 14 dígitos
 * sem pontuação, e a máscara existe só no elemento. Assim o formulário não
 * precisa lembrar de limpar nada antes de enviar — é impossível vazar
 * pontuação para a API por esquecimento.
 *
 * A máscara é reescrita no próprio elemento em vez de depender só da ligação
 * `[value]`: caracteres descartados (letras, por exemplo) não mudam o valor
 * formatado, então o Angular não reescreveria o campo e o caractere inválido
 * ficaria visível.
 */
@Component({
  selector: 'cc-cnpj-input',
  imports: [InputText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CnpjInputComponent),
      multi: true,
    },
  ],
  template: `
    <input
      pInputText
      type="text"
      inputmode="numeric"
      autocomplete="off"
      placeholder="00.000.000/0000-00"
      [fluid]="true"
      [id]="inputId()"
      [value]="display()"
      [disabled]="isDisabled()"
      [attr.aria-describedby]="describedBy() || null"
      (input)="onInput($event)"
      (blur)="onBlur()"
    />
  `,
})
export class CnpjInputComponent implements ControlValueAccessor {
  readonly inputId = input('cnpj');
  readonly describedBy = input('');

  protected readonly display = signal('');
  protected readonly isDisabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.display.set(formatCnpj(value));
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

  protected onInput(event: Event): void {
    const element = event.target as HTMLInputElement;
    const digits = normalizeCnpj(element.value);
    const masked = formatCnpj(digits);

    element.value = masked;
    this.display.set(masked);
    // O que sai para o formulário — e daí para a API — nunca tem pontuação.
    this.onChange(digits);
  }

  protected onBlur(): void {
    this.onTouched();
  }
}
