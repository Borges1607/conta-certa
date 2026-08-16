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
} from '../app/core/models/enums';

/**
 * Estado do "backend" do mock — Parte 7, §1.
 *
 * As entidades espelham o modelo de domínio da spec do backend, não os DTOs da
 * API. Os handlers fazem a tradução, exatamente como um servidor faria. É isso
 * que mantém o mock honesto: se ele guardasse DTOs, esconderia as regras que
 * ele deve aplicar no lugar do frontend.
 */

export interface DbInstitution {
  id: string;
  name: string;
  cnpj: string;
  contactEmail: string;
  contactPhone: string;
  active: boolean;
  version: number;
  createdAt: string;
}

export interface DbUser {
  id: string;
  role: Role;
  status: AccountStatus;
  fullName: string;
  email: string;
  /** Texto puro: é um mock, e nada aqui sai do navegador. */
  password: string;
  registrationNumber: string | null;
  institutionId: string | null;
  emailVerified: boolean;
  mustChangePassword: boolean;
  version: number;
  createdAt: string;
}

export interface DbSession {
  refreshToken: string;
  accessToken: string;
  userId: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
  revoked: boolean;
}

export type ActionTokenType = 'EMAIL_VERIFICATION' | 'PASSWORD_RESET' | 'TEACHER_INVITE';

export interface DbActionToken {
  token: string;
  type: ActionTokenType;
  userId: string;
  expiresAt: string;
  usedAt: string | null;
}

export interface DbRoom {
  id: string;
  teacherId: string;
  institutionId: string;
  name: string;
  description: string | null;
  grade: Grade;
  contentTopics: string[];
  passingScorePercent: number;
  joinCode: string;
  archivedAt: string | null;
  /** Sala que nunca teve aluno nem tentativa pode ser excluída. */
  everUsed: boolean;
  version: number;
  createdAt: string;
}

export interface DbMembership {
  roomId: string;
  studentId: string;
  status: MembershipStatus;
  joinedAt: string;
  removedAt: string | null;
}

export interface DbLesson {
  id: string;
  teacherId: string;
  title: string;
  summary: string;
  theoryMarkdown: string;
  status: ContentStatus;
  version: number;
  createdAt: string;
}

export interface DbOption {
  id: string;
  text: string;
  correct: boolean;
}

export interface DbQuestion {
  id: string;
  lessonId: string;
  type: QuestionType;
  prompt: string;
  explanation: string;
  order: number;
  active: boolean;
  options: DbOption[];
  correctBoolean: boolean | null;
  correctNumericValue: string | null;
  absoluteTolerance: string | null;
  unit: NumericUnit;
  decimalPlaces: number;
  version: number;
}

export interface DbAssignment {
  id: string;
  roomId: string;
  lessonId: string;
  position: number;
  status: ContentStatus;
  availableFrom: string | null;
  dueAt: string | null;
  timeLimitMinutes: number | null;
  maxAttempts: number | null;
  questionCount: number | null;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  version: number;
}

export interface DbAnswer {
  questionSnapshotId: string;
  answeredAt: string;
  selectedOptionIds: string[] | null;
  booleanValue: boolean | null;
  numericValue: string | null;
  correct: boolean;
}

export interface DbQuestionSnapshot {
  id: string;
  questionId: string;
  order: number;
  type: QuestionType;
  prompt: string;
  explanation: string;
  options: DbOption[];
  correctBoolean: boolean | null;
  correctNumericValue: string | null;
  absoluteTolerance: string | null;
  unit: NumericUnit;
  decimalPlaces: number;
}

export interface DbAttempt {
  id: string;
  assignmentId: string;
  studentId: string;
  status: AttemptStatus;
  startedAt: string;
  expiresAt: string | null;
  submittedAt: string | null;
  questions: DbQuestionSnapshot[];
  answers: DbAnswer[];
  /** Preenchidos na correção. */
  correctAnswers: number;
  scorePercent: number;
  passed: boolean;
  stars: number;
  xpEarned: number;
  idempotencyKey: string | null;
}

export interface DbExtraAttempts {
  assignmentId: string;
  studentId: string;
  quantity: number;
}

export interface DbVideo {
  id: string;
  teacherId: string;
  title: string;
  description: string;
  url: string;
  category: string;
  status: ContentStatus;
  version: number;
  createdAt: string;
}

export interface DbMaterial {
  id: string;
  teacherId: string;
  title: string;
  description: string;
  kind: MaterialKind;
  url: string | null;
  fileId: string | null;
  category: string;
  status: ContentStatus;
  version: number;
  createdAt: string;
}

export interface DbFile {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  /** Conteúdo simbólico: o mock não guarda bytes reais. */
  content: string;
}

export interface DbMediaAssignment {
  id: string;
  roomId: string;
  mediaType: MediaViewType;
  mediaId: string;
  lessonId: string | null;
  publishedAt: string;
}

export interface DbMediaView {
  mediaType: MediaViewType;
  mediaId: string;
  studentId: string;
  firstViewedAt: string;
  lastViewedAt: string;
}

export interface DbFinancialTip {
  id: string;
  title: string;
  content: string;
  sourceUrl: string | null;
  publicationDate: string;
  active: boolean;
  version: number;
}

export interface Database {
  institutions: DbInstitution[];
  users: DbUser[];
  sessions: DbSession[];
  actionTokens: DbActionToken[];
  rooms: DbRoom[];
  memberships: DbMembership[];
  lessons: DbLesson[];
  questions: DbQuestion[];
  assignments: DbAssignment[];
  attempts: DbAttempt[];
  extraAttempts: DbExtraAttempts[];
  videos: DbVideo[];
  materials: DbMaterial[];
  files: DbFile[];
  mediaAssignments: DbMediaAssignment[];
  mediaViews: DbMediaView[];
  financialTips: DbFinancialTip[];
}

const STORAGE_KEY = 'cc.mock.db';

let database: Database | null = null;

/** Estado atual, semeado na primeira chamada. */
export function db(): Database {
  database ??= restore() ?? seedDatabase();
  return database;
}

/** Persiste após cada mutação, para o estado sobreviver à recarga. */
export function persist(): void {
  if (!database) {
    return;
  }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(database));
  } catch {
    // Sem storage o mock continua funcionando, só não sobrevive à recarga.
  }
}

/** Recomeça do zero — usado pelo painel de desenvolvimento. */
export function resetDatabase(): void {
  database = seedDatabase();
  persist();
}

function restore(): Database | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Database) : null;
  } catch {
    return null;
  }
}

let seedFactory: (() => Database) | null = null;

/** Registrado por `seed.ts`, para evitar dependência circular. */
export function registerSeed(factory: () => Database): void {
  seedFactory = factory;
}

function seedDatabase(): Database {
  if (!seedFactory) {
    throw new Error('Semente do mock não registrada. Importe src/mocks/seed.ts.');
  }
  return seedFactory();
}
