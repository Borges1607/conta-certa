import type { AttemptStatus } from '../../../core/models/enums';

/**
 * Relatórios do professor — §7.5 da spec de integração.
 *
 * **Nenhum número daqui é calculado no frontend.** Tudo o que aparece na tela,
 * no gráfico e na versão de impressão vem destes tipos, exatamente como a API
 * devolveu (§11 da spec).
 */

/** Atalho de período. `ALL` remove `from`/`to` da requisição. */
export type ReportPeriod = 'LAST_30_DAYS' | 'CUSTOM' | 'ALL';

export interface ReportFilters {
  roomId: string | null;
  lessonId: string | null;
  period: ReportPeriod;
  /** Instantes ISO 8601 UTC. Nulos quando `period` é `ALL`. */
  from: string | null;
  to: string | null;
}

export interface ReportOverviewMetrics {
  studentCount: number;
  activeStudentCount: number;
  attemptCount: number;
  submittedAttemptCount: number;
  averageScorePercent: number | null;
  passRatePercent: number | null;
  completionPercent: number | null;
}

/** Um ponto da evolução de tentativas. `date` é `YYYY-MM-DD`. */
export interface AttemptsPoint {
  date: string;
  attempts: number;
  submitted: number;
}

/** Uma faixa da distribuição de notas, rotulada pelo servidor. */
export interface ScoreBucket {
  label: string;
  count: number;
}

export interface LessonCompletion {
  lessonId: string;
  lessonTitle: string;
  completedStudents: number;
  totalStudents: number;
  completionPercent: number;
  averageScorePercent: number | null;
}

export interface ReportOverview {
  metrics: ReportOverviewMetrics;
  attemptsOverTime: AttemptsPoint[];
  scoreDistribution: ScoreBucket[];
  lessonCompletion: LessonCompletion[];
  /** Instante em que o servidor gerou os números. Vai na impressão. */
  generatedAt: string;
}

export interface ReportStudentRow {
  studentId: string;
  fullName: string;
  registrationNumber: string | null;
  attemptCount: number;
  averageScorePercent: number | null;
  bestScorePercent: number | null;
  completedLessons: number;
  totalLessons: number;
  xp: number;
  stars: number;
  lastActivityAt: string | null;
}

export interface ReportAttemptRow {
  attemptId: string;
  lessonId: string;
  lessonTitle: string;
  attemptNumber: number;
  status: AttemptStatus;
  scorePercent: number | null;
  passed: boolean | null;
  startedAt: string;
  submittedAt: string | null;
}

/** O professor vê nomes completos das próprias salas — §10 da spec. */
export interface ReportRankingRow {
  position: number;
  studentId: string;
  fullName: string;
  registrationNumber: string | null;
  xp: number;
  stars: number;
  completedLessons: number;
}

/** Opção de sala no filtro. Só o necessário para escolher. */
export interface ReportRoomOption {
  id: string;
  name: string;
  archived: boolean;
}
