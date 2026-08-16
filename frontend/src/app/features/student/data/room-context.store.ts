import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

import type { RoomSummary } from '../../../core/models/room';
import { createPageState } from '../../../shared/forms/page-state';
import { StudentRoomService } from './student-room.service';

/**
 * Contexto da sala atual — Parte 4, §2.
 *
 * Cumpre o princípio 4 da visão geral ("isolamento por sala") de um jeito que
 * não depende de ninguém lembrar de limpar cache: **o `roomId` da URL é a única
 * fonte de verdade**, e trocar de sala descarta o estado anterior antes de
 * carregar o novo.
 *
 * Por que ler da URL em vez de receber por `input()`: o shell do aluno é o
 * componente-pai da rota e não tem `:roomId` no próprio caminho, mas precisa do
 * contexto para o seletor de sala, o XP e o menu. Ler do `Router` mantém uma
 * fonte só — e é o que faz recarga e botão "voltar" do navegador continuarem
 * corretos.
 *
 * Fornecido no nível da rota do aluno, não em `root`: ao sair da área do aluno,
 * o injetor da rota é destruído e nada sobra em memória.
 */
@Injectable()
export class RoomContextStore {
  private readonly roomService = inject(StudentRoomService);
  private readonly router = inject(Router);

  private readonly roomIdSignal = signal<string | null>(null);

  /** Sala atual, sempre derivada do parâmetro de rota. */
  readonly roomId = this.roomIdSignal.asReadonly();

  /** Salas do aluno — `GET /student/rooms`. */
  readonly rooms = createPageState(() => this.roomService.listRooms());

  /**
   * Dashboard da sala atual — `GET /student/rooms/{roomId}/dashboard`.
   *
   * O `load()` disparado na troca de sala coloca o estado em `loading`
   * imediatamente, então nenhum número da sala anterior fica visível enquanto
   * o novo não chega.
   */
  readonly dashboard = createPageState(async () => {
    const roomId = this.roomIdSignal();
    if (!roomId) {
      throw new Error('Nenhuma sala selecionada.');
    }
    return this.roomService.dashboard(roomId);
  });

  /** Sala atual pelo nome, para cabeçalho e trilha de navegação. */
  readonly currentRoom = computed<RoomSummary | null>(() => {
    const roomId = this.roomIdSignal();
    if (!roomId) {
      return null;
    }
    const fromDashboard = this.dashboard.data();
    if (fromDashboard && fromDashboard.room.id === roomId) {
      return fromDashboard.room;
    }
    return this.rooms.data()?.find((room) => room.id === roomId) ?? null;
  });

  readonly currentRoomName = computed(() => this.currentRoom()?.name ?? 'Sala');

  constructor() {
    void this.rooms.load();
    this.syncFromUrl();

    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.syncFromUrl());
  }

  /** Recarrega a lista de salas — usado após entrar em uma sala nova. */
  async reloadRooms(): Promise<void> {
    await this.rooms.refresh();
  }

  /** Recarrega o dashboard mantendo o conteúdo visível. */
  async refreshDashboard(): Promise<void> {
    if (this.roomIdSignal()) {
      await this.dashboard.refresh();
    }
  }

  private syncFromUrl(): void {
    this.setRoom(findRoomId(this.router.routerState.snapshot.root));
  }

  /**
   * Troca a sala corrente.
   *
   * Só reage a mudanças reais: navegar entre telas da mesma sala não recarrega
   * o dashboard. Mudou de sala — ou saiu para a lista —, o estado anterior é
   * descartado na hora.
   */
  private setRoom(roomId: string | null): void {
    if (roomId === this.roomIdSignal()) {
      return;
    }

    this.roomIdSignal.set(roomId);

    if (roomId) {
      void this.dashboard.load();
    }
  }
}

/** Procura `roomId` em toda a árvore de rotas ativa, do topo para a folha. */
function findRoomId(root: ActivatedRouteSnapshot): string | null {
  let current: ActivatedRouteSnapshot | null = root;

  while (current) {
    const value: unknown = current.params['roomId'];
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
    current = current.firstChild;
  }

  return null;
}
