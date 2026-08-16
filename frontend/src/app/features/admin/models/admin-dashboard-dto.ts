/**
 * `GET /admin/dashboard` — §8.1 da spec de integração.
 *
 * Só contagens: o admin não vê ranking nem conteúdo (§10 da spec).
 */

export interface InstitutionCounts {
  total: number;
  active: number;
  inactive: number;
}

export interface TeacherCounts {
  total: number;
  pending: number;
  active: number;
  inactive: number;
}

export interface AdminDashboard {
  institutions: InstitutionCounts;
  teachers: TeacherCounts;
}
