import type { AccountStatus, Role } from './enums';
import type { InstitutionSummary } from './institution';

/** Usuário autenticado — §3 da spec de integração. */
export interface UserSummary {
  id: string;
  role: Role;
  status: AccountStatus;
  fullName: string;
  email: string;
  registrationNumber: string | null;
  /** Nulo para o admin global. */
  institution: InstitutionSummary | null;
  emailVerified: boolean;
  mustChangePassword: boolean;
}

/** `PATCH /me` altera somente o nome — §4.1 da spec. */
export interface UpdateProfileRequest {
  fullName: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}