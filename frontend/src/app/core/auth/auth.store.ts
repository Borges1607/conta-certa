import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, shareReplay, tap, throwError } from 'rxjs';

import { ApiError } from '../api/problem-details';
import type { AuthTokens, LoginRequest } from '../models/auth';
import type { Role } from '../models/enums';
import type { ChangePasswordRequest, UpdateProfileRequest, UserSummary } from '../models/user';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';

/** Rota inicial de cada perfil — §5 da visão geral das specs. */
export function homePathForRole(role: Role): string {
  switch (role) {
    case 'ADMIN':
      return '/admin';
    case 'TEACHER':
      return '/professor';
    case 'STUDENT':
      return '/aluno/salas';
  }
}

/**
 * Sessão do usuário — Parte 1, §4.2.
 *
 * O ponto delicado é o `refresh()`: a §11 da spec de integração exige que o
 * cliente trate refresh concorrente com **uma única operação em andamento**.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly auth = inject(AuthService);
  private readonly storage = inject(TokenStorage);
  private readonly router = inject(Router);

  private readonly userSignal = signal<UserSummary | null>(null);

  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.userSignal() !== null);
  readonly role = computed<Role | null>(() => this.userSignal()?.role ?? null);
  readonly mustChangePassword = computed(() => this.userSignal()?.mustChangePassword ?? false);
  readonly institution = computed(() => this.userSignal()?.institution ?? null);

  /**
   * A operação de refresh em andamento, se houver.
   *
   * É este campo — e o fato de `refresh()` devolver o mesmo observable
   * compartilhado — que garante uma única chamada a `/auth/refresh` mesmo com
   * dezenas de requisições recebendo `401` ao mesmo tempo.
   */
  private refreshInFlight: Observable<AuthTokens> | null = null;

  constructor() {
    this.hydrateFromStorage();

    // Logout em outra aba derruba a sessão aqui também.
    effect(() => {
      if (this.storage.clearedExternally() > 0 && this.userSignal() !== null) {
        this.clearSession();
        void this.router.navigate(['/login']);
      }
    });
  }

  /** Restaura a sessão persistida. Chamado no bootstrap. */
  hydrateFromStorage(): void {
    const user = this.storage.user();
    const refreshToken = this.storage.refreshToken();
    // Sem refresh token não há sessão recuperável, mesmo que sobre um usuário.
    this.userSignal.set(refreshToken ? user : null);
    if (!refreshToken) {
      this.storage.clear();
    }
  }

  async login(credentials: LoginRequest): Promise<UserSummary> {
    const response = await firstValueFrom(this.auth.login(credentials));
    this.storage.saveTokens(response);
    this.storage.saveUser(response.user);
    this.userSignal.set(response.user);
    return response.user;
  }

  /**
   * Rotaciona os tokens. Concorrentes compartilham a mesma operação.
   *
   * `shareReplay` com `refCount: false` mantém o resultado disponível para
   * quem se inscrever depois de a chamada já ter terminado; `finalize` limpa o
   * campo para que o próximo `401` inicie uma operação nova.
   */
  refresh(): Observable<AuthTokens> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }

    const refreshToken = this.storage.refreshToken();
    if (!refreshToken) {
      return throwError(
        () =>
          new ApiError({
            status: 401,
            code: 'NO_REFRESH_TOKEN',
            detail: 'Sua sessão expirou. Entre novamente.',
          }),
      );
    }

    this.refreshInFlight = this.auth.refresh(refreshToken).pipe(
      tap((tokens) => this.storage.saveTokens(tokens)),
      catchError((error: unknown) => {
        this.clearSession();
        return throwError(() => error);
      }),
      finalize(() => {
        this.refreshInFlight = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    return this.refreshInFlight;
  }

  /** Revoga a sessão atual no servidor e limpa o estado local. */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.auth.logout());
    } catch {
      // A limpeza local acontece de qualquer forma: um servidor indisponível
      // não pode prender o usuário numa sessão que ele quer encerrar.
    }
    this.clearSession();
    await this.router.navigate(['/login']);
  }

  /** Recarrega `/me` — usado após ações que mudam o próprio usuário. */
  async loadCurrentUser(): Promise<UserSummary> {
    const user = await firstValueFrom(this.auth.me());
    this.storage.saveUser(user);
    this.userSignal.set(user);
    return user;
  }

  async updateProfile(body: UpdateProfileRequest): Promise<UserSummary> {
    const user = await firstValueFrom(this.auth.updateProfile(body));
    this.storage.saveUser(user);
    this.userSignal.set(user);
    return user;
  }

  /** Após trocar a senha, `mustChangePassword` deixa de bloquear a navegação. */
  async changePassword(body: ChangePasswordRequest): Promise<void> {
    await firstValueFrom(this.auth.changePassword(body));
    const current = this.userSignal();
    if (current?.mustChangePassword) {
      const updated: UserSummary = { ...current, mustChangePassword: false };
      this.storage.saveUser(updated);
      this.userSignal.set(updated);
    }
  }

  /** Limpa o estado local sem chamar o servidor. */
  clearSession(): void {
    this.refreshInFlight = null;
    this.storage.clear();
    this.userSignal.set(null);
  }

  /** Sessão perdida: limpa e volta ao login preservando o destino. */
  async expireSession(returnUrl?: string): Promise<void> {
    this.clearSession();
    const url = returnUrl ?? this.router.url;
    const isPublic = url.startsWith('/login') || url === '/';
    await this.router.navigate(['/login'], {
      queryParams: isPublic ? {} : { returnUrl: url },
    });
  }

  /** Rota inicial do usuário atual. */
  homePath(): string {
    const role = this.role();
    return role ? homePathForRole(role) : '/login';
  }
}
