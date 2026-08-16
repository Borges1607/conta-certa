import type { Grade, MembershipStatus } from './enums';
import type { InstitutionSummary } from './institution';

/** Sala vista pelo aluno — §3 da spec de integração. */
export interface RoomSummary {
  id: string;
  name: string;
  description: string | null;
  grade: Grade;
  contentTopics: string[];
  teacher: { id: string; fullName: string };
  institution: InstitutionSummary;
  membershipStatus: MembershipStatus;
  archived: boolean;
  /** Presente nas listagens do aluno. */
  progressPercent?: number;
}