/**
 * `LocalDate` — Parte 6, §6.
 *
 * `publicationDate` é uma data pura. Passá-la por `new Date('2026-08-15')` a
 * interpreta como meia-noite **UTC** e, em qualquer fuso a oeste de Greenwich,
 * o seletor mostra o dia anterior; `toISOString()` na volta desloca de novo.
 * Este módulo faz o caminho de ida e volta usando só componentes locais, então
 * o dia escolhido é exatamente o dia enviado, em qualquer fuso do navegador.
 *
 * A serialização (`Date` → `YYYY-MM-DD`) vive em `core/util/format.ts`
 * (`toLocalDateString`); aqui fica a desserialização e as comparações.
 */

const LOCAL_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

/** `YYYY-MM-DD` → `Date` na meia-noite **local**, sem conversão de fuso. */
export function parseLocalDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const match = LOCAL_DATE_PATTERN.exec(value);
  if (!match) {
    return null;
  }
  const [, year, month, day] = match;
  return new Date(Number(year), Number(month) - 1, Number(day));
}

export function isLocalDate(value: string | null | undefined): boolean {
  return Boolean(value && LOCAL_DATE_PATTERN.test(value));
}

/**
 * Hoje em `YYYY-MM-DD`, pelo calendário local.
 *
 * Deliberadamente **não** usa `toISOString().slice(0, 10)`: à noite no Brasil
 * isso já devolveria o dia seguinte.
 */
export function todayLocalDate(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Comparação de datas puras — texto, nunca instante. */
export function compareLocalDate(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Rótulo do agendamento: passado, hoje ou futuro. */
export type ScheduleRelation = 'past' | 'today' | 'future';

export function scheduleRelation(publicationDate: string, today: string): ScheduleRelation {
  const comparison = compareLocalDate(publicationDate, today);
  if (comparison === 0) {
    return 'today';
  }
  return comparison < 0 ? 'past' : 'future';
}
