import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { AdminDashboard } from '../models/admin-dashboard-dto';

/** `GET /admin/dashboard` — §8.1 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class AdminDashboardService {
  private readonly api = inject(ApiClient);

  /** Nenhuma rota da API aparece fora daqui — visão geral, §7. */
  private readonly path = '/admin/dashboard';

  load(): Promise<AdminDashboard> {
    return firstValueFrom(this.api.get<AdminDashboard>(this.path));
  }
}
