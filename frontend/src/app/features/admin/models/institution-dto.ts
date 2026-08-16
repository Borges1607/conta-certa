import type { InstitutionSummary } from '../../../core/models/institution';

/** Instituições na visão do admin — §8.2 da spec de integração. */

/**
 * Recurso editável: por isso carrega `version`, que volta em todo `PATCH`
 * (§3 da spec).
 *
 * `teacherCount` e `studentCount` são o "resumo de vínculos" da Parte 6, §4.
 * São opcionais porque a API pode não fornecê-los na listagem.
 */
export interface AdminInstitution extends InstitutionSummary {
  version: number;
  createdAt?: string;
  updatedAt?: string;
  teacherCount?: number | null;
  studentCount?: number | null;
}

/** `POST /admin/institutions`. `cnpj` vai com 14 dígitos, sem pontuação. */
export interface CreateInstitutionRequest {
  name: string;
  cnpj: string;
  contactEmail: string;
  /** E.164, por exemplo `+5548999999999`. */
  contactPhone: string;
}

/**
 * `PATCH /admin/institutions/{id}`.
 *
 * `version` é **obrigatório**: sem ele o servidor não consegue detectar
 * conflito e o critério da §11 da spec cai por terra.
 */
export interface PatchInstitutionRequest {
  name?: string;
  cnpj?: string;
  contactEmail?: string;
  contactPhone?: string;
  version: number;
}

/** Situação usada no filtro da lista e nos cartões do dashboard. */
export type InstitutionActiveFilter = 'true' | 'false';

export interface InstitutionFilters {
  /** Busca por nome ou CNPJ. */
  search?: string;
  active?: boolean;
}
