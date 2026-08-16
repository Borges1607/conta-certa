import type { MembershipStatus } from '../../../core/models/enums';

/**
 * Aluno de uma sala na visão do professor — §7.1 da spec de integração.
 *
 * XP, estrelas e lições concluídas vêm prontos da API. O frontend não recalcula
 * nada disso (§11 da spec).
 */
export interface RoomStudent {
  studentId: string;
  fullName: string;
  registrationNumber: string | null;
  email: string;
  /** XP acumulado **nesta** sala. Isolamento por sala, §11 da spec. */
  xp: number;
  completedLessons: number;
  totalLessons: number;
  stars: number;
  lastActivityAt: string | null;
  membershipStatus: MembershipStatus;
}

/** `POST /teacher/room-lessons/{assignmentId}/students/{studentId}/extra-attempts`. */
export interface ExtraAttemptsRequest {
  quantity: number;
}

/**
 * Resposta da concessão de tentativas extras.
 *
 * `attemptsAvailable` nulo significa "sem limite" — a atribuição não limita
 * tentativas. O texto de retorno usa exatamente o que a API devolveu.
 */
export interface ExtraAttemptsResult {
  assignmentId: string;
  studentId: string;
  extraAttemptsGranted: number;
  attemptsUsed: number;
  attemptsAvailable: number | null;
}
