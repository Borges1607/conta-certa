import type { MediaGroup, MediaLessonLink } from './media';

/** Item de mídia agrupável: só precisa do vínculo opcional com a lição. */
interface Groupable {
  lesson: MediaLessonLink | null;
}

const UNLINKED_KEY = '__geral__';

/**
 * Agrupa vídeos ou materiais por lição — Parte 4, §7.
 *
 * Isto é organização de tela, não regra de negócio: a API entrega a lista e o
 * vínculo, e a ordem de chegada é preservada dentro de cada grupo. Mídias sem
 * lição vão para "Geral da sala", no fim.
 */
export function groupByLesson<T extends Groupable>(items: readonly T[]): MediaGroup<T>[] {
  const groups = new Map<string, MediaGroup<T>>();

  for (const item of items) {
    const key = item.lesson?.lessonId ?? UNLINKED_KEY;
    const title = item.lesson?.lessonTitle ?? 'Geral da sala';

    const group = groups.get(key) ?? { key, title, items: [] };
    group.items.push(item);
    groups.set(key, group);
  }

  const ordered = [...groups.values()];
  // O grupo sem lição fecha a lista: ele é o "resto", não o começo.
  return ordered
    .filter((group) => group.key !== UNLINKED_KEY)
    .concat(ordered.filter((group) => group.key === UNLINKED_KEY));
}
