/** Instituições — §3 e §5.2 da spec de integração. */

export interface InstitutionSummary {
  id: string;
  name: string;
  cnpj: string;
  contactEmail: string;
  contactPhone: string;
  active: boolean;
}

/** Retorno não paginado de `GET /institutions/options?active=true`. */
export interface InstitutionOption {
  id: string;
  name: string;
  cnpj: string;
}