import type { Page, PageQuery } from '../app/core/models/page';
import type { InstitutionSummary } from '../app/core/models/institution';
import type { UserSummary } from '../app/core/models/user';
import type { Role } from '../app/core/models/enums';
import { db, persist, type DbInstitution, type DbUser } from './db';
import { forbidden, unauthorized, type MockContext } from './router';

/** Utilidades compartilhadas pelos handlers do mock. */

let idCounter = 0;

export function newId(prefix: string): string {
  idCounter++;
  return `${prefix}-${idCounter}-${Math.random().toString(36).slice(2, 8)}`;
}

export function nowIso(): string {
  return new Date().toISOString();
}

export function isoIn(ms: number): string {
  return new Date(Date.now() + ms).toISOString();
}

export function isPast(iso: string | null): boolean {
  return iso !== null && Date.parse(iso) <= Date.now();
}

export function isFuture(iso: string | null): boolean {
  return iso !== null && Date.parse(iso) > Date.now();
}

/** Usuário autenticado pelo access token, ou `401`. */
export function requireUser(context: MockContext): DbUser {
  const token = context.accessToken;
  if (!token) {
    throw unauthorized('Token ausente.');
  }

  const session = db().sessions.find((s) => s.accessToken === token && !s.revoked);
  if (!session) {
    throw unauthorized('Token inválido ou revogado.');
  }
  if (isPast(session.accessExpiresAt)) {
    // Exercita o ciclo de refresh de verdade em desenvolvimento.
    throw unauthorized('Token expirado.');
  }

  const user = db().users.find((u) => u.id === session.userId);
  if (!user || user.status === 'INACTIVE') {
    throw unauthorized('Sessão encerrada.');
  }

  return user;
}

export function requireRole(context: MockContext, ...roles: Role[]): DbUser {
  const user = requireUser(context);
  if (!roles.includes(user.role)) {
    throw forbidden(`Esta ação exige o perfil ${roles.join(' ou ')}.`);
  }
  return user;
}

export function institutionOf(id: string | null): DbInstitution | null {
  return id ? (db().institutions.find((i) => i.id === id) ?? null) : null;
}

export function toInstitutionSummary(institution: DbInstitution): InstitutionSummary {
  return {
    id: institution.id,
    name: institution.name,
    cnpj: institution.cnpj,
    contactEmail: institution.contactEmail,
    contactPhone: institution.contactPhone,
    active: institution.active,
  };
}

export function toUserSummary(user: DbUser): UserSummary {
  const institution = institutionOf(user.institutionId);
  return {
    id: user.id,
    role: user.role,
    status: user.status,
    fullName: user.fullName,
    email: user.email,
    registrationNumber: user.registrationNumber,
    institution: institution ? toInstitutionSummary(institution) : null,
    emailVerified: user.emailVerified,
    mustChangePassword: user.mustChangePassword,
  };
}

/** Nome parcialmente anonimizado do colega — §6.4 da spec de integração. */
export function anonymizeName(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  if (parts.length === 1) {
    return parts[0];
  }
  return `${parts[0]} ${parts[parts.length - 1].charAt(0).toUpperCase()}.`;
}

export function readPageQuery(context: MockContext): Required<Pick<PageQuery, 'page' | 'size'>> & {
  sort?: string;
} {
  const page = Number(context.query.get('page') ?? 0);
  const size = Number(context.query.get('size') ?? 20);
  const sort = context.query.get('sort') ?? undefined;

  return {
    page: Number.isFinite(page) && page >= 0 ? page : 0,
    size: Number.isFinite(size) ? Math.min(Math.max(size, 1), 100) : 20,
    sort,
  };
}

export function paginate<T>(items: readonly T[], context: MockContext): Page<T> {
  const { page, size, sort } = readPageQuery(context);
  const sorted = sort ? applySort([...items], sort) : [...items];
  const start = page * size;

  return {
    content: sorted.slice(start, start + size),
    page,
    size,
    totalElements: sorted.length,
    totalPages: Math.max(1, Math.ceil(sorted.length / size)),
  };
}

function applySort<T>(items: T[], sort: string): T[] {
  const [field, direction = 'asc'] = sort.split(',');
  const factor = direction === 'desc' ? -1 : 1;

  return items.sort((a, b) => {
    const left = (a as Record<string, unknown>)[field];
    const right = (b as Record<string, unknown>)[field];

    if (typeof left === 'number' && typeof right === 'number') {
      return (left - right) * factor;
    }
    return String(left ?? '').localeCompare(String(right ?? ''), 'pt-BR') * factor;
  });
}

export function matchesSearch(term: string | null, ...fields: (string | null)[]): boolean {
  if (!term) {
    return true;
  }
  const needle = term.trim().toLowerCase();
  return fields.some((field) => (field ?? '').toLowerCase().includes(needle));
}

/** Aplica a mutação e persiste. */
export function mutate<T>(fn: () => T): T {
  const result = fn();
  persist();
  return result;
}

/** Gera um código de sala de 6 caracteres, sem ambiguidade visual. */
export function generateJoinCode(): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  return db().rooms.some((r) => r.joinCode === code) ? generateJoinCode() : code;
}

export function onlyDigits(value: string): string {
  return value.replace(/\D/g, '');
}
