import type { Page } from '../../../core/models/page';

/**
 * Ranking da sala — §6.4 da spec de integração e Parte 4, §8.
 *
 * **O frontend não faz nenhuma anonimização própria.** `displayName` já chega
 * da API com primeiro nome e inicial do sobrenome. Repare que este tipo não
 * tem `email` nem `fullName`: é impossível renderizar na tela um dado que o
 * modelo não carrega.
 */
export interface RankingEntry {
  position: number;
  studentId: string;
  /** Já anonimizado pela API — `Ana L.`. */
  displayName: string;
  xp: number;
  stars: number;
  lessonsPassed: number;
  /** Verdadeiro na linha do próprio aluno. */
  me: boolean;
}

/**
 * Página do ranking.
 *
 * `me` vem junto para a tela mostrar a posição do próprio aluno mesmo quando
 * ela está fora da página visível (Parte 4, §8).
 */
export interface StudentRankingPage extends Page<RankingEntry> {
  me: RankingEntry | null;
}
