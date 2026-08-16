import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type { RoomSummary } from '../../../core/models/room';
import type { StudentDashboard } from '../models/dashboard';

/** Corpo de `POST /student/rooms/join` — §6.1 da spec de integração. */
export interface JoinRoomRequest {
  code: string;
}

/**
 * Salas do aluno — §6.1 da spec de integração.
 *
 * Todas as rotas de API do aluno ficam nos services de `data/`; nenhum literal
 * de rota aparece em componente (visão geral, §7).
 *
 * Repare que **não existe** método para sair da sala: o aluno não pode fazê-lo
 * (Parte 4, §3), e a ausência aqui é o que garante que nenhuma tela invente a
 * ação.
 */
@Injectable({ providedIn: 'root' })
export class StudentRoomService {
  private readonly api = inject(ApiClient);

  /** Salas ativas do aluno. */
  listRooms(): Promise<RoomSummary[]> {
    return firstValueFrom(this.api.get<RoomSummary[]>('/student/rooms'));
  }

  /** Entrada por código de 6 caracteres. */
  join(code: string): Promise<RoomSummary> {
    const body: JoinRoomRequest = { code };
    return firstValueFrom(this.api.post<RoomSummary>('/student/rooms/join', body));
  }

  /** Fonte única do dashboard da sala. */
  dashboard(roomId: string): Promise<StudentDashboard> {
    return firstValueFrom(
      this.api.get<StudentDashboard>(`/student/rooms/${roomId}/dashboard`),
    );
  }
}
