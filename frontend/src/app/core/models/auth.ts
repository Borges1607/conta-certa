import type { UserSummary } from './user';

/** Autenticação — §4 da spec de integração. */

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  /** Segundos. O access token dura 15 minutos. */
  accessExpiresIn: number;
  /** Segundos. O refresh token dura 7 dias e é rotacionado a cada uso. */
  refreshExpiresIn: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type LoginResponse = AuthTokens & { user: UserSummary };

export interface RefreshRequest {
  refreshToken: string;
}

export interface StudentRegistrationRequest {
  fullName: string;
  email: string;
  password: string;
  registrationNumber: string;
  institutionId: string;
}

export interface TokenRequest {
  token: string;
}

export interface ResendVerificationRequest {
  email: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface AcceptTeacherInviteRequest {
  token: string;
  password: string;
}

/**
 * Regra de senha — §4.1 da spec: 8 a 72 caracteres, ao menos uma letra e um
 * número. A validação local é conveniência; o backend sempre valida.
 */
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 72;

export function isPasswordValid(value: string): boolean {
  return (
    value.length >= PASSWORD_MIN_LENGTH &&
    value.length <= PASSWORD_MAX_LENGTH &&
    /[a-zA-Z]/.test(value) &&
    /[0-9]/.test(value)
  );
}
