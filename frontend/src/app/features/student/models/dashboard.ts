import type { RoomSummary } from '../../../core/models/room';
import type { StudentAchievement } from './achievement';

/**
 * Visão agregada da sala — `GET /student/rooms/{roomId}/dashboard`, §6.1 da
 * spec de integração.
 *
 * Esta é a **fonte única** da tela do dashboard (Parte 4, §4). Todo número
 * abaixo vem pronto: progresso, nível, XP, estrelas, lições concluídas e
 * posição no ranking. Nada é derivado de nada — inclusive `levelProgressPercent`,
 * que existe justamente para a barra de XP não precisar de aritmética.
 */

/** Dica financeira do dia — §8.3 da spec. O conteúdo é Markdown. */
export interface FinancialTip {
  id: string;
  title: string;
  content: string;
  sourceUrl: string | null;
  publicationDate: string;
}

/** "Continuar de onde parou" — a próxima lição disponível, escolhida pela API. */
export interface NextLessonPointer {
  assignmentId: string;
  lessonId: string;
  title: string;
  order: number;
  /** Preenchido quando existe tentativa em andamento nessa lição. */
  activeAttemptId: string | null;
}

export interface StudentDashboard {
  room: RoomSummary;
  progressPercent: number;
  level: number;
  xpTotal: number;
  /** Percentual já percorrido dentro do nível atual, de 0 a 100. */
  levelProgressPercent: number;
  /** `null` quando o aluno está no último nível previsto. */
  xpToNextLevel: number | null;
  starsTotal: number;
  starsPossible: number;
  lessonsCompleted: number;
  lessonsTotal: number;
  rankingPosition: number | null;
  rankingParticipants: number;
  nextLesson: NextLessonPointer | null;
  recentAchievements: StudentAchievement[];
  tipOfDay: FinancialTip | null;
}
