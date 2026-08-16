import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { TeacherDashboard } from '../models/dashboard';

/** `GET /teacher/dashboard` — §7.1 da spec de integração. */
@Injectable({ providedIn: 'root' })
export class TeacherDashboardService {
  private readonly api = inject(ApiClient);

  load(): Promise<TeacherDashboard> {
    return firstValueFrom(this.api.get<TeacherDashboard>('/teacher/dashboard'));
  }
}
