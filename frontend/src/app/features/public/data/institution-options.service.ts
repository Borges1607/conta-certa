import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import { publicContext } from '../../../core/api/http-context';
import type { InstitutionOption } from '../../../core/models/institution';

/**
 * Instituições para selects públicos — §5.2 da spec de integração.
 *
 * Este endpoint **não é paginado**: devolve a lista completa das ativas.
 */
@Injectable({ providedIn: 'root' })
export class InstitutionOptionsService {
  private readonly api = inject(ApiClient);

  listActive(): Promise<InstitutionOption[]> {
    return firstValueFrom(
      this.api.get<InstitutionOption[]>('/institutions/options', {
        params: { active: true },
        context: publicContext(),
      }),
    );
  }
}
