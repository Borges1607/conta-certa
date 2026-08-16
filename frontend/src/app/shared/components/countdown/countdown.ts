import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';

import { formatDuration } from '../../../core/util/format';
import { ServerClock } from '../../../core/util/server-clock';

/** Aviso aos 5 minutos e ao 1 minuto restantes. */
const WARNING_MS = 5 * 60_000;
const DANGER_MS = 60_000;

/**
 * Cronômetro de tentativa — Parte 2, §4.4.
 *
 * Atende dois critérios da §11 da spec de integração: "cronômetros usam
 * `expiresAt` do servidor" e "sobrevivem a recarga/fechamento da página".
 *
 * O valor é **sempre recalculado** a partir do instante absoluto, nunca
 * decrementado. É por isso que ele está correto ao voltar de uma aba suspensa,
 * de uma máquina que hibernou ou de uma recarga: não existe contador local para
 * ficar defasado.
 *
 * Este componente não decide expiração — quem expira e corrige a tentativa é o
 * servidor. Ele apenas avisa o pai, que envia o `submit`.
 */
@Component({
  selector: 'cc-countdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="countdown"
      [class.countdown--warning]="level() === 'warning'"
      [class.countdown--danger]="level() === 'danger'"
      role="timer"
      [attr.aria-live]="level() === 'danger' ? 'assertive' : 'off'"
      [attr.aria-label]="ariaLabel()"
    >
      <i class="pi pi-clock" aria-hidden="true"></i>
      <span class="countdown__value">{{ display() }}</span>
    </span>
  `,
  styleUrl: './countdown.scss',
})
export class CountdownComponent implements OnInit {
  private readonly clock = inject(ServerClock);
  private readonly destroyRef = inject(DestroyRef);

  /** Instante ISO 8601 UTC de expiração, vindo da API. */
  readonly expiresAt = input.required<string>();

  /** Emitido uma única vez, quando o tempo acaba. */
  readonly expired = output<void>();

  private readonly remaining = signal(0);
  private alreadyExpired = false;

  readonly display = computed(() => formatDuration(this.remaining()));

  readonly level = computed<'normal' | 'warning' | 'danger'>(() => {
    const ms = this.remaining();
    if (ms <= DANGER_MS) {
      return 'danger';
    }
    return ms <= WARNING_MS ? 'warning' : 'normal';
  });

  readonly ariaLabel = computed(() => `Tempo restante: ${this.display()}`);

  ngOnInit(): void {
    this.tick();

    const interval = setInterval(() => this.tick(), 1000);
    this.destroyRef.onDestroy(() => clearInterval(interval));
  }

  private tick(): void {
    const ms = this.clock.remainingMs(this.expiresAt());
    this.remaining.set(ms);

    if (ms <= 0 && !this.alreadyExpired) {
      this.alreadyExpired = true;
      this.expired.emit();
    }
  }
}
