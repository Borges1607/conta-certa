import type { AccountStatus } from '../../../core/models/enums';
import type { InstitutionSummary } from '../../../core/models/institution';

/** Professores na visão do admin — §8.1 da spec de integração. */

export interface AdminTeacher {
  id: string;
  fullName: string;
  email: string;
  registrationNumber: string | null;
  institution: InstitutionSummary | null;
  status: AccountStatus;
  emailVerified: boolean;
  version: number;
  createdAt?: string;
  updatedAt?: string;
  lastLoginAt?: string | null;
}

/**
 * `POST /admin/teachers`.
 *
 * Note a ausência deliberada de qualquer campo de senha: o resultado é um
 * professor `PENDING` que recebe convite por e-mail e define a própria senha
 * (Parte 6, §5 e §9).
 */
export interface CreateTeacherRequest {
  fullName: string;
  email: string;
  registrationNumber: string;
  institutionId: string;
}

/** `PATCH /admin/teachers/{id}`. E-mail não é editável. */
export interface PatchTeacherRequest {
  fullName?: string;
  registrationNumber?: string;
  institutionId?: string;
  version: number;
}

export interface TeacherFilters {
  /** Busca por nome, e-mail ou matrícula. */
  search?: string;
  status?: AccountStatus;
  institutionId?: string;
}
