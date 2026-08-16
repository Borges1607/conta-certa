import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../api/api-client';
import { publicContext } from '../api/http-context';
import type {
  AcceptTeacherInviteRequest,
  AuthTokens,
  ForgotPasswordRequest,
  LoginRequest,
  LoginResponse,
  RefreshRequest,
  ResendVerificationRequest,
  ResetPasswordRequest,
  StudentRegistrationRequest,
  TokenRequest,
} from '../models/auth';
import type { ChangePasswordRequest, UpdateProfileRequest, UserSummary } from '../models/user';

/**
 * Endpoints de autenticação e conta — §4.1 da spec de integração.
 *
 * Camada fina e sem estado: quem guarda sessão é o `AuthStore`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClient);

  login(body: LoginRequest): Observable<LoginResponse> {
    return this.api.post<LoginResponse>('/auth/login', body, { context: publicContext() });
  }

  /** O refresh token é rotacionado a cada uso — §2.2 da spec. */
  refresh(refreshToken: string): Observable<AuthTokens> {
    const body: RefreshRequest = { refreshToken };
    return this.api.post<AuthTokens>('/auth/refresh', body, { context: publicContext() });
  }

  /** Revoga apenas a sessão atual — §2.2 da spec. */
  logout(): Observable<void> {
    return this.api.post<void>('/auth/logout');
  }

  /** Responde `202 Accepted`: o aluno só autentica depois de confirmar o e-mail. */
  registerStudent(body: StudentRegistrationRequest): Observable<void> {
    return this.api.post<void>('/auth/student-registration', body, { context: publicContext() });
  }

  verifyEmail(token: string): Observable<void> {
    const body: TokenRequest = { token };
    return this.api.post<void>('/auth/verify-email', body, { context: publicContext() });
  }

  resendVerification(email: string): Observable<void> {
    const body: ResendVerificationRequest = { email };
    return this.api.post<void>('/auth/resend-verification', body, { context: publicContext() });
  }

  forgotPassword(email: string): Observable<void> {
    const body: ForgotPasswordRequest = { email };
    return this.api.post<void>('/auth/forgot-password', body, { context: publicContext() });
  }

  resetPassword(body: ResetPasswordRequest): Observable<void> {
    return this.api.post<void>('/auth/reset-password', body, { context: publicContext() });
  }

  acceptTeacherInvite(body: AcceptTeacherInviteRequest): Observable<void> {
    return this.api.post<void>('/auth/accept-teacher-invite', body, { context: publicContext() });
  }

  me(): Observable<UserSummary> {
    return this.api.get<UserSummary>('/me');
  }

  updateProfile(body: UpdateProfileRequest): Observable<UserSummary> {
    return this.api.patch<UserSummary>('/me', body);
  }

  changePassword(body: ChangePasswordRequest): Observable<void> {
    return this.api.post<void>('/me/change-password', body);
  }
}
