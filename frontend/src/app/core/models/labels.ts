import type {
  AccountStatus,
  AttemptStatus,
  ContentStatus,
  Grade,
  MaterialKind,
  MediaViewType,
  MembershipStatus,
  NumericUnit,
  QuestionType,
  Role,
} from './enums';

/**
 * Rótulos em português dos enums do contrato.
 *
 * Esta é a **única** fonte de tradução. Nenhum template deve conter um `@switch`
 * de rótulo — use o pipe `ccEnumLabel` (Parte 2, §5).
 */

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrador',
  TEACHER: 'Professor',
  STUDENT: 'Aluno',
};

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  PENDING: 'Convite enviado',
  ACTIVE: 'Ativo',
  INACTIVE: 'Inativo',
};

export const GRADE_LABELS: Record<Grade, string> = {
  HIGH_SCHOOL_1: '1º ano do ensino médio',
  HIGH_SCHOOL_2: '2º ano do ensino médio',
  HIGH_SCHOOL_3: '3º ano do ensino médio',
};

export const GRADE_SHORT_LABELS: Record<Grade, string> = {
  HIGH_SCHOOL_1: '1º ano',
  HIGH_SCHOOL_2: '2º ano',
  HIGH_SCHOOL_3: '3º ano',
};

export const CONTENT_STATUS_LABELS: Record<ContentStatus, string> = {
  DRAFT: 'Rascunho',
  PUBLISHED: 'Publicado',
  ARCHIVED: 'Arquivado',
};

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  SINGLE_CHOICE: 'Escolha única',
  MULTIPLE_CHOICE: 'Múltipla escolha',
  TRUE_FALSE: 'Verdadeiro ou falso',
  NUMERIC: 'Numérica',
};

export const ATTEMPT_STATUS_LABELS: Record<AttemptStatus, string> = {
  IN_PROGRESS: 'Em andamento',
  SUBMITTED: 'Finalizada',
  EXPIRED: 'Expirada',
};

export const MATERIAL_KIND_LABELS: Record<MaterialKind, string> = {
  FILE: 'Arquivo',
  EXTERNAL_LINK: 'Link externo',
};

export const MEDIA_VIEW_TYPE_LABELS: Record<MediaViewType, string> = {
  VIDEO: 'Videoaula',
  MATERIAL: 'Material',
};

export const NUMERIC_UNIT_LABELS: Record<NumericUnit, string> = {
  BRL: 'Reais (R$)',
  PERCENT: 'Porcentagem (%)',
  NONE: 'Sem unidade',
};

export const MEMBERSHIP_STATUS_LABELS: Record<MembershipStatus, string> = {
  ACTIVE: 'Ativa',
  REMOVED: 'Removida',
};

/** Todos os mapas, para o pipe `ccEnumLabel` resolver por nome. */
export const ENUM_LABELS = {
  Role: ROLE_LABELS,
  AccountStatus: ACCOUNT_STATUS_LABELS,
  Grade: GRADE_LABELS,
  GradeShort: GRADE_SHORT_LABELS,
  ContentStatus: CONTENT_STATUS_LABELS,
  QuestionType: QUESTION_TYPE_LABELS,
  AttemptStatus: ATTEMPT_STATUS_LABELS,
  MaterialKind: MATERIAL_KIND_LABELS,
  MediaViewType: MEDIA_VIEW_TYPE_LABELS,
  NumericUnit: NUMERIC_UNIT_LABELS,
  MembershipStatus: MEMBERSHIP_STATUS_LABELS,
} as const;

export type EnumLabelKind = keyof typeof ENUM_LABELS;