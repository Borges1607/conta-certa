import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import type { UserSummary } from '../models/user';
import { AuthStore } from './auth.store';
import { authGuard, guestGuard, passwordChangeGuard, roleGuard } from './guards';
import { TokenStorage } from './token-storage';

const user = (overrides: Partial<UserSummary> = {}): UserSummary => ({
  id: 'u1',
  role: 'TEACHER',
  status: 'ACTIVE',
  fullName: 'Ana Lima',
  email: 'ana@example.com',
  registrationNumber: '2026001',
  institution: null,
  emailVerified: true,
  mustChangePassword: false,
  ...overrides,
});

describe('guards', () => {
  let storage: TokenStorage;

  const route = {} as ActivatedRouteSnapshot;
  const state = (url: string) => ({ url }) as RouterStateSnapshot;

  const signIn = (u: UserSummary) => {
    storage.saveTokens({
      accessToken: 'a',
      refreshToken: 'r',
      tokenType: 'Bearer',
      accessExpiresIn: 900,
      refreshExpiresIn: 604_800,
    });
    storage.saveUser(u);
    TestBed.inject(AuthStore).hydrateFromStorage();
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    storage = TestBed.inject(TokenStorage);
  });

  afterEach(() => localStorage.clear());

  it('authGuard manda ao login preservando o destino', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard(route, state('/professor/salas')),
    ) as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/login');
    expect(result.queryParams['returnUrl']).toBe('/professor/salas');
  });

  it('authGuard libera usuário autenticado', () => {
    signIn(user());
    expect(TestBed.runInInjectionContext(() => authGuard(route, state('/professor')))).toBe(true);
  });

  it('roleGuard libera o perfil certo e bloqueia o errado', () => {
    signIn(user({ role: 'TEACHER' }));

    expect(
      TestBed.runInInjectionContext(() => roleGuard('TEACHER')(route, state('/professor'))),
    ).toBe(true);

    const bloqueado = TestBed.runInInjectionContext(() =>
      roleGuard('ADMIN')(route, state('/admin')),
    ) as UrlTree;
    expect(bloqueado.toString()).toContain('/403');
  });

  it('roleGuard aceita vários perfis', () => {
    signIn(user({ role: 'STUDENT' }));
    expect(
      TestBed.runInInjectionContext(() =>
        roleGuard('STUDENT', 'TEACHER')(route, state('/aluno/salas')),
      ),
    ).toBe(true);
  });

  it('guestGuard tira o autenticado da tela de login, para a home do perfil', () => {
    signIn(user({ role: 'STUDENT' }));
    const result = TestBed.runInInjectionContext(() =>
      guestGuard(route, state('/login')),
    ) as UrlTree;
    expect(result.toString()).toContain('/aluno/salas');
  });

  it('guestGuard deixa o visitante entrar no login', () => {
    expect(TestBed.runInInjectionContext(() => guestGuard(route, state('/login')))).toBe(true);
  });

  it('passwordChangeGuard força a troca de senha obrigatória', () => {
    signIn(user({ mustChangePassword: true }));
    const result = TestBed.runInInjectionContext(() =>
      passwordChangeGuard(route, state('/professor/salas')),
    ) as UrlTree;
    expect(result.toString()).toContain('/conta/senha');
  });

  it('passwordChangeGuard não atrapalha quem já trocou', () => {
    signIn(user({ mustChangePassword: false }));
    expect(
      TestBed.runInInjectionContext(() => passwordChangeGuard(route, state('/professor'))),
    ).toBe(true);
  });
});
