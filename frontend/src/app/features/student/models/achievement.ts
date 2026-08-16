/**
 * Conquistas calculadas por sala — §6.4 da spec de integração.
 *
 * As sete conquistas da primeira versão são fixas, mas quem decide se estão
 * desbloqueadas é a API. A tela não compara XP nem conta lições aprovadas.
 */

export type AchievementCode =
  | 'FIRST_PASS'
  | 'FIRST_PERFECT_SCORE'
  | 'XP_100'
  | 'XP_500'
  | 'XP_1000'
  | 'FIVE_LESSONS_PASSED'
  | 'TEN_LESSONS_PASSED';

export interface StudentAchievement {
  code: AchievementCode;
  title: string;
  /** Critério da conquista, exibido também quando ela está bloqueada. */
  description: string;
  icon: string | null;
  unlocked: boolean;
  unlockedAt: string | null;
  /**
   * Progresso atual, "quando a API o fornece" (Parte 4, §8). Exibido como
   * texto — `3 de 5` —, sem barra derivada: qualquer razão entre os dois seria
   * cálculo do frontend.
   */
  progressCurrent: number | null;
  progressTarget: number | null;
}
