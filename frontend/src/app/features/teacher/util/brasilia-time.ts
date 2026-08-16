import { environment } from '../../../../environments/environment';
import { toInstantString } from '../../../core/util/format';

/**
 * Ponte entre o `p-datepicker` e o contrato da API — Parte 5, §7.
 *
 * O professor pensa em horário de Brasília; a API fala UTC. O `p-datepicker`
 * devolve um `Date` cujo **relógio de parede** é o que a pessoa escolheu na
 * tela. Estas duas funções fazem a ida e a volta, e a conversão final para
 * texto continua sendo de `toInstantString` (`core/util/format.ts`), que é o
 * único lugar autorizado a produzir o ISO 8601 UTC enviado.
 *
 * Quando o navegador já está em `America/Sao_Paulo` — o caso normal — o
 * resultado é idêntico a `date.toISOString()`. A correção só entra em ação se o
 * professor abrir o sistema de outro fuso, e é o que garante que a virada do
 * dia seja enviada corretamente em qualquer situação.
 */

const TZ = environment.presentationTimeZone;

/** Rótulo curto exibido ao lado de todo campo de data e hora. */
export const TIME_ZONE_LABEL = 'horário de Brasília';

const partsFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

interface WallClock {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function wallClockIn(instant: Date): WallClock {
  const parts = partsFormatter.formatToParts(instant);
  const get = (type: Intl.DateTimeFormatPartTypes): number => {
    const found = parts.find((part) => part.type === type)?.value ?? '0';
    return Number(found);
  };
  // `hour12: false` produz 24 na meia-noite em alguns runtimes.
  const hour = get('hour') % 24;
  return {
    year: get('year'),
    month: get('month'),
    day: get('day'),
    hour,
    minute: get('minute'),
    second: get('second'),
  };
}

/** Deslocamento do fuso de apresentação naquele instante, em milissegundos. */
function offsetMsAt(instant: Date): number {
  const wall = wallClockIn(instant);
  const asIfUtc = Date.UTC(wall.year, wall.month - 1, wall.day, wall.hour, wall.minute, wall.second);
  return asIfUtc - instant.getTime();
}

/**
 * `Date` escolhido no seletor → instante ISO 8601 UTC.
 *
 * O relógio de parede do `Date` é lido como horário de Brasília.
 */
export function pickedDateToInstant(picked: Date | null | undefined): string | null {
  if (!picked || Number.isNaN(picked.getTime())) {
    return null;
  }

  const wallAsUtc = Date.UTC(
    picked.getFullYear(),
    picked.getMonth(),
    picked.getDate(),
    picked.getHours(),
    picked.getMinutes(),
    0,
  );

  // Duas passagens resolvem a borda de horário de verão: a primeira estimativa
  // usa o deslocamento aproximado, a segunda o confirma no instante correto.
  let instant = wallAsUtc - offsetMsAt(new Date(wallAsUtc));
  instant = wallAsUtc - offsetMsAt(new Date(instant));

  return toInstantString(new Date(instant));
}

/**
 * Instante ISO 8601 UTC → `Date` para o seletor, com o relógio de parede de
 * Brasília. É a inversa exata de `pickedDateToInstant`.
 */
export function instantToPickedDate(iso: string | null | undefined): Date | null {
  if (!iso) {
    return null;
  }

  const instant = new Date(iso);
  if (Number.isNaN(instant.getTime())) {
    return null;
  }

  const wall = wallClockIn(instant);
  return new Date(wall.year, wall.month - 1, wall.day, wall.hour, wall.minute, 0, 0);
}

/** Início do dia, em Brasília, `days` dias atrás — usado no filtro padrão. */
export function startOfDayDaysAgo(days: number, now = new Date()): string | null {
  const wall = wallClockIn(now);
  const local = new Date(wall.year, wall.month - 1, wall.day, 0, 0, 0, 0);
  local.setDate(local.getDate() - days);
  return pickedDateToInstant(local);
}

/** Fim do dia atual em Brasília — limite superior do filtro padrão. */
export function endOfToday(now = new Date()): string | null {
  const wall = wallClockIn(now);
  const local = new Date(wall.year, wall.month - 1, wall.day, 23, 59, 0, 0);
  return pickedDateToInstant(local);
}
