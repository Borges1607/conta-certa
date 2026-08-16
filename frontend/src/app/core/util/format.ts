import { environment } from '../../../environments/environment';

/**
 * Formatação de apresentação — Parte 1, §7.
 *
 * A API trafega instantes em ISO 8601 UTC; a interface mostra em
 * `America/Sao_Paulo`. Essa conversão acontece **somente aqui** e nos pipes que
 * chamam estas funções.
 */

const TZ = environment.presentationTimeZone;
const LOCALE = 'pt-BR';

const dateTimeFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TZ,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const dateFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TZ,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

const timeFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TZ,
  hour: '2-digit',
  minute: '2-digit',
});

const longDateFormatter = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TZ,
  day: '2-digit',
  month: 'long',
  year: 'numeric',
});

const moneyFormatter = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: 'BRL',
});

/** Instante ISO 8601 UTC → `15/08/2026 16:30` no fuso de apresentação. */
export function formatDateTime(iso: string | null | undefined): string {
  const date = parseInstant(iso);
  return date ? dateTimeFormatter.format(date) : '—';
}

/** Instante ISO 8601 UTC → `15/08/2026`. */
export function formatDate(iso: string | null | undefined): string {
  const date = parseInstant(iso);
  return date ? dateFormatter.format(date) : '—';
}

export function formatTime(iso: string | null | undefined): string {
  const date = parseInstant(iso);
  return date ? timeFormatter.format(date) : '—';
}

export function formatLongDate(iso: string | null | undefined): string {
  const date = parseInstant(iso);
  return date ? longDateFormatter.format(date) : '—';
}

/**
 * Data pura `YYYY-MM-DD` → `15/08/2026`.
 *
 * Deliberadamente **não** passa por `Date`: `publicationDate` é um `LocalDate`
 * e tratá-lo como instante desloca o dia conforme o fuso do navegador
 * (Parte 6, §6).
 */
export function formatLocalDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : value;
}

/** `Date` do seletor → `YYYY-MM-DD`, sem conversão de fuso. */
export function toLocalDateString(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** `Date` do seletor → instante ISO 8601 UTC, para enviar à API. */
export function toInstantString(date: Date): string {
  return date.toISOString();
}

/**
 * Valor monetário — a API manda **string decimal**, nunca `float`.
 * A conversão para número acontece só aqui, para exibir.
 */
export function formatMoney(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? moneyFormatter.format(parsed) : value;
}

/** Percentual de 0 a 100 — §2.1 da spec. */
export function formatPercent(value: number | null | undefined, decimals = 0): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '—';
  }
  return `${value.toFixed(decimals).replace('.', ',')}%`;
}

/** Duração em milissegundos → `05:32` ou `1:05:32`. */
export function formatDuration(ms: number): string {
  const safe = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;

  const mm = String(minutes).padStart(2, '0');
  const ss = String(seconds).padStart(2, '0');

  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** Minutos → `30 minutos`, `1 hora e 30 minutos`, `sem limite`. */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) {
    return 'sem limite';
  }
  if (minutes < 60) {
    return `${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  const hourPart = `${hours} ${hours === 1 ? 'hora' : 'horas'}`;
  return rest === 0 ? hourPart : `${hourPart} e ${rest} ${rest === 1 ? 'minuto' : 'minutos'}`;
}

/** Tamanho de arquivo em bytes → `4,2 MB`. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(1).replace('.', ',')} ${units[unit]}`;
}

/** Tempo relativo — `agora`, `há 5 minutos`, `há 2 dias`. */
export function formatRelativeTime(iso: string | null | undefined, nowMs: number): string {
  const date = parseInstant(iso);
  if (!date) {
    return '—';
  }

  const diffSeconds = Math.round((nowMs - date.getTime()) / 1000);
  const future = diffSeconds < 0;
  const abs = Math.abs(diffSeconds);

  if (abs < 45) {
    return 'agora';
  }

  const steps: [number, string, string][] = [
    [60, 'minuto', 'minutos'],
    [3600, 'hora', 'horas'],
    [86_400, 'dia', 'dias'],
    [2_592_000, 'mês', 'meses'],
    [31_536_000, 'ano', 'anos'],
  ];

  let value = abs;
  let label = 'segundo';
  let plural = 'segundos';

  for (const [divisor, singular, pluralForm] of steps) {
    if (abs >= divisor) {
      value = Math.floor(abs / divisor);
      label = singular;
      plural = pluralForm;
    }
  }

  const unit = value === 1 ? label : plural;
  return future ? `em ${value} ${unit}` : `há ${value} ${unit}`;
}

/** Primeiro nome e inicial do sobrenome. Ver a ressalva abaixo. */
export function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  return parts
    .slice(0, 2)
    .map((p) => p.charAt(0).toUpperCase())
    .join('');
}

function parseInstant(iso: string | null | undefined): Date | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}
