import { Pipe, PipeTransform, inject } from '@angular/core';

import { ENUM_LABELS, type EnumLabelKind } from '../../core/models/labels';
import {
  formatDate,
  formatDateTime,
  formatDuration,
  formatFileSize,
  formatLocalDate,
  formatLongDate,
  formatMinutes,
  formatMoney,
  formatPercent,
  formatRelativeTime,
  formatTime,
} from '../../core/util/format';
import { ServerClock } from '../../core/util/server-clock';

/**
 * Pipes de apresentação — Parte 2, §5.
 *
 * Todos delegam para `core/util/format`, que é o único lugar que conhece o
 * fuso de apresentação. Nenhum template formata data por conta própria.
 */

/** Instante UTC → `15/08/2026 16:30` em America/Sao_Paulo. */
@Pipe({ name: 'ccDateTime' })
export class DateTimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDateTime(value);
  }
}

/** Instante UTC → `15/08/2026`. */
@Pipe({ name: 'ccDate' })
export class DatePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDate(value);
  }
}

/** Instante UTC → `16:30`. */
@Pipe({ name: 'ccTime' })
export class TimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatTime(value);
  }
}

/** Instante UTC → `15 de agosto de 2026`. */
@Pipe({ name: 'ccLongDate' })
export class LongDatePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatLongDate(value);
  }
}

/**
 * `LocalDate` (`YYYY-MM-DD`) → `15/08/2026`, sem conversão de fuso.
 * Usar `ccDate` aqui deslocaria o dia — ver Parte 6, §6.
 */
@Pipe({ name: 'ccLocalDate' })
export class LocalDatePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatLocalDate(value);
  }
}

/** String decimal → `R$ 1.250,50`. Nunca recebe `number`. */
@Pipe({ name: 'ccMoney' })
export class MoneyPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatMoney(value);
  }
}

/** Número de 0 a 100 → `70%`. */
@Pipe({ name: 'ccPercent' })
export class PercentPipe implements PipeTransform {
  transform(value: number | null | undefined, decimals = 0): string {
    return formatPercent(value, decimals);
  }
}

/** Milissegundos → `05:32`. */
@Pipe({ name: 'ccDuration' })
export class DurationPipe implements PipeTransform {
  transform(ms: number | null | undefined): string {
    return formatDuration(ms ?? 0);
  }
}

/** Minutos → `30 minutos` ou `sem limite` quando nulo. */
@Pipe({ name: 'ccMinutes' })
export class MinutesPipe implements PipeTransform {
  transform(minutes: number | null | undefined): string {
    return formatMinutes(minutes);
  }
}

/** Bytes → `4,2 MB`. */
@Pipe({ name: 'ccFileSize' })
export class FileSizePipe implements PipeTransform {
  transform(bytes: number | null | undefined): string {
    return formatFileSize(bytes ?? 0);
  }
}

/**
 * Tempo relativo com base no relógio do servidor.
 *
 * Impuro de propósito: o valor muda com o tempo, não com a entrada.
 */
@Pipe({ name: 'ccRelativeTime', pure: false })
export class RelativeTimePipe implements PipeTransform {
  private readonly clock = inject(ServerClock);

  transform(value: string | null | undefined): string {
    return formatRelativeTime(value, this.clock.now());
  }
}

/**
 * Rótulo em português de um enum do contrato.
 *
 * ```html
 * {{ room.grade | ccEnumLabel: 'Grade' }}
 * ```
 */
@Pipe({ name: 'ccEnumLabel' })
export class EnumLabelPipe implements PipeTransform {
  transform(value: string | null | undefined, kind: EnumLabelKind): string {
    if (!value) {
      return '—';
    }
    const map = ENUM_LABELS[kind] as Record<string, string>;
    return map[value] ?? value;
  }
}

/** Conjunto pronto para importar num componente. */
export const CC_FORMAT_PIPES = [
  DateTimePipe,
  DatePipe,
  TimePipe,
  LongDatePipe,
  LocalDatePipe,
  MoneyPipe,
  PercentPipe,
  DurationPipe,
  MinutesPipe,
  FileSizePipe,
  RelativeTimePipe,
  EnumLabelPipe,
] as const;
