import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

import type { AuthTokens } from '../models/auth';
import type { UserSummary } from '../models/user';

const ACCESS_TOKEN_KEY = 'cc.accessToken';
const REFRESH_TOKEN_KEY = 'cc.refreshToken';
const ACCESS_EXPIRES_AT_KEY = 'cc.accessExpiresAt';
const USER_KEY = 'cc.user';

const ALL_KEYS = [ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, ACCESS_EXPIRES_AT_KEY, USER_KEY];

/**
 * Persistência da sessão — Parte 1, §4.1.
 *
 * `localStorage` e não memória: o critério "cronômetros sobrevivem a recarga e
 * fechamento da página" implica sessão sobrevivente à recarga. Arquivos
 * privados continuam saindo exclusivamente pelos endpoints autorizados.
 *
 * Este é o **único** arquivo da aplicação autorizado a tocar em `localStorage`
 * (garantido por regra de ESLint).
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private readonly document = inject(DOCUMENT);

  /** Emite quando outra aba encerrou a sessão. */
  readonly clearedExternally = signal(0);

  constructor() {
    // Logout em uma aba derruba a sessão nas demais.
    this.document.defaultView?.addEventListener('storage', (event) => {
      if (event.key === null || ALL_KEYS.includes(event.key)) {
        if (!this.read(REFRESH_TOKEN_KEY)) {
          this.clearedExternally.update((n) => n + 1);
        }
      }
    });
  }

  accessToken(): string | null {
    return this.read(ACCESS_TOKEN_KEY);
  }

  refreshToken(): string | null {
    return this.read(REFRESH_TOKEN_KEY);
  }

  /** Instante absoluto de expiração do access token, em milissegundos. */
  accessExpiresAt(): number | null {
    const raw = this.read(ACCESS_EXPIRES_AT_KEY);
    if (!raw) {
      return null;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }

  user(): UserSummary | null {
    const raw = this.read(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as UserSummary;
    } catch {
      return null;
    }
  }

  saveTokens(tokens: AuthTokens): void {
    this.write(ACCESS_TOKEN_KEY, tokens.accessToken);
    this.write(REFRESH_TOKEN_KEY, tokens.refreshToken);
    // O instante absoluto é calculado no recebimento: guardar a duração
    // relativa não sobreviveria a uma recarga.
    this.write(ACCESS_EXPIRES_AT_KEY, String(Date.now() + tokens.accessExpiresIn * 1000));
  }

  saveUser(user: UserSummary): void {
    this.write(USER_KEY, JSON.stringify(user));
  }

  clear(): void {
    for (const key of ALL_KEYS) {
      this.remove(key);
    }
  }

  private storage(): Storage | null {
    try {
      return this.document.defaultView?.localStorage ?? null;
    } catch {
      // Modo privado restrito ou storage bloqueado por política.
      return null;
    }
  }

  private read(key: string): string | null {
    try {
      return this.storage()?.getItem(key) ?? null;
    } catch {
      return null;
    }
  }

  private write(key: string, value: string): void {
    try {
      this.storage()?.setItem(key, value);
    } catch {
      // Sem storage a sessão vale só para esta aba. Não é motivo para quebrar.
    }
  }

  private remove(key: string): void {
    try {
      this.storage()?.removeItem(key);
    } catch {
      /* idem */
    }
  }
}
