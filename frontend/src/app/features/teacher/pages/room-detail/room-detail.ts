import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Button } from 'primeng/button';

import { ApiError } from '../../../../core/api/problem-details';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../../core/util/submitting';
import { ErrorStateComponent } from '../../../../shared/components/error-state/error-state';
import { LoadingSkeletonComponent } from '../../../../shared/components/loading-skeleton/loading-skeleton';
import { PageHeaderComponent, type Crumb } from '../../../../shared/components/page-header/page-header';
import { createPageState } from '../../../../shared/forms/page-state';
import { ArchivedBannerComponent } from '../../components/archived-banner/archived-banner';
import { JoinCodePanelComponent } from '../../components/join-code-panel/join-code-panel';
import { RoomFormDialogComponent } from '../../components/room-form-dialog/room-form-dialog';
import { RoomMediaTabComponent } from '../../components/room-media-tab/room-media-tab';
import { RoomStudentsTabComponent } from '../../components/room-students-tab/room-students-tab';
import { RoomTrackTabComponent } from '../../components/room-track-tab/room-track-tab';
import { TeacherRoomService } from '../../data/teacher-room.service';

/** Abas do detalhe. O valor vai no fragmento da URL. */
const TABS = ['visao-geral', 'alunos', 'trilha', 'midias'] as const;
type TabId = (typeof TABS)[number];

interface TabDef {
  id: TabId;
  label: string;
  icon: string;
}

/**
 * Detalhe da sala — Parte 5, §2 e §4.
 *
 * A aba aberta vive no **fragmento da URL**. Não é detalhe de implementação:
 * é o que faz "manda o link da aba de alunos" funcionar e sobreviver a uma
 * recarga.
 *
 * Sala arquivada é somente leitura em todas as abas — o `archived` desce para
 * cada uma delas, e não há caminho para uma mutação escapar.
 */
@Component({
  selector: 'cc-room-detail-page',
  imports: [
    Button,
    PageHeaderComponent,
    LoadingSkeletonComponent,
    ErrorStateComponent,
    ArchivedBannerComponent,
    JoinCodePanelComponent,
    RoomFormDialogComponent,
    RoomStudentsTabComponent,
    RoomTrackTabComponent,
    RoomMediaTabComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './room-detail.html',
  styleUrl: './room-detail.scss',
})
export class RoomDetailPage {
  private readonly rooms = inject(TeacherRoomService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly roomId = input.required<string>();

  protected readonly state = createPageState(() => this.rooms.get(this.roomId()));

  protected readonly tabs: readonly TabDef[] = [
    { id: 'visao-geral', label: 'Visão geral', icon: 'pi pi-info-circle' },
    { id: 'alunos', label: 'Alunos', icon: 'pi pi-users' },
    { id: 'trilha', label: 'Trilha', icon: 'pi pi-map' },
    { id: 'midias', label: 'Mídias', icon: 'pi pi-play' },
  ];

  protected readonly activeTab = signal<TabId>('visao-geral');
  protected readonly formVisible = signal(false);
  protected readonly regenerating = signal(false);

  private readonly guard = createSubmitGuard();

  protected readonly archived = computed(() => this.state.data()?.archived ?? false);

  protected readonly crumbs = computed<Crumb[]>(() => [
    { label: 'Salas', link: '/professor/salas' },
    { label: this.state.data()?.name ?? 'Sala' },
  ]);

  constructor() {
    effect(() => {
      this.roomId();
      void this.state.load();
    });

    // O fragmento manda: entrar por link direto abre a aba certa.
    const fragment = this.router.parseUrl(this.router.url).fragment;
    if (fragment && TABS.includes(fragment as TabId)) {
      this.activeTab.set(fragment as TabId);
    }
  }

  protected selectTab(tab: TabId): void {
    this.activeTab.set(tab);
    void this.router.navigate([], { fragment: tab, replaceUrl: true });
  }

  protected async onSaved(): Promise<void> {
    await this.state.refresh();
  }

  protected async regenerateCode(): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: 'Gerar um código novo?',
      message:
        'O código atual deixará de funcionar imediatamente. Alunos já matriculados continuam na sala; só quem ainda não entrou precisará do código novo.',
      acceptLabel: 'Gerar novo código',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      this.regenerating.set(true);
      try {
        await this.rooms.regenerateCode(this.roomId());
        this.notify.success('Código regenerado', 'Distribua o novo código para a turma.');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível gerar o código.');
      } finally {
        this.regenerating.set(false);
      }
    });
  }

  protected async archive(): Promise<void> {
    const confirmed = await this.notify.destructive({
      header: 'Arquivar esta sala?',
      message:
        'A sala ficará somente leitura: alunos não poderão entrar nem fazer novas tentativas. O histórico é preservado.',
      acceptLabel: 'Arquivar',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      try {
        await this.rooms.archive(this.roomId());
        this.notify.success('Sala arquivada');
        await this.state.refresh();
      } catch (error) {
        this.notify.error(error instanceof ApiError ? error : 'Não foi possível arquivar a sala.');
      }
    });
  }
}
