/**
 * Enums normativos da API — §2.5 da spec de integração.
 *
 * São union types de string, não `enum` do TypeScript: o valor que trafega é
 * exatamente o que está escrito aqui, sem camada de tradução.
 */

export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT';

export type AccountStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE';

export type Grade = 'HIGH_SCHOOL_1' | 'HIGH_SCHOOL_2' | 'HIGH_SCHOOL_3';

export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'NUMERIC';

export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'EXPIRED';

export type MaterialKind = 'FILE' | 'EXTERNAL_LINK';

export type MediaViewType = 'VIDEO' | 'MATERIAL';

/** Unidade de uma questão numérica — §7.2 da spec. */
export type NumericUnit = 'BRL' | 'PERCENT' | 'NONE';

/** Situação da matrícula do aluno na sala — §3 da spec. */
export type MembershipStatus = 'ACTIVE' | 'REMOVED';