import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { InputText } from 'primeng/inputtext';

import { formatPhone, nationalDigits, toE164 } from '../../util/phone';

/**
 * Entrada de telefone — Parte 6, §4.
 *
 * Mesma divisão do CNPJ: máscara `(48) 99999-9999` na tela, E.164
 * (`+5548999999999`) no valor do controle e, portanto, no corpo da requisição.
 */
@Component({
  selector: 'cc-phone-input',
  imports: [InputText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PhoneInputComponent),
      multi: true,
    },
  ],
  template: `
    <input
      pInputText
      type="tel"
      inputmode="tel"
      autocomplete="tel"
      placeholder="(48) 99999-9999"
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
export class PhoneInputComponent implements ControlValueAccessor {
  readonly inputId = input('phone');
  readonly describedBy = input('');

  protected readonly display = signal('');
  protected readonly isDisabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.display.set(formatPhone(value));
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
    const digits = nationalDigits(element.value);
    const masked = formatPhone(digits);

    element.value = masked;
    this.display.set(masked);
    this.onChange(digits === '' ? '' : toE164(digits));
  }

  protected onBlur(): void {
    this.onTouched();
  }
}
