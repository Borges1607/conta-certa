import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';

import { CountdownComponent } from '../../../../shared/components/countdown/countdown';
import { StarRatingComponent } from '../../../../shared/components/star-rating/star-rating';
import { DateTimePipe } from '../../../../shared/pipes/format.pipes';
import type { LessonAvailability, LessonTrackItem } from '../../models/lesson-track';
import { LockReasonComponent } from '../lock-reason/lock-reason';

interface NodeState {
  /** Rótulo textual do estado — obrigatório, nunca só cor (Parte 4, §5.1). */
  label: string;
  /** Ícone grande dentro do círculo. */
  icon: string;
  modifier: string;
}

/**
 * Estação da trilha — Parte 4, §5.1.
 *
 * No caminho aparecem só duas coisas: o disco com o ícone do estado — cadeado
 * quando bloqueada, tique quando aprovada, play quando liberada — e a placa com
 * o nome da lição. Nota, estrelas, tentativas, prazo e motivo do bloqueio vão
 * para o modal, aberto ao tocar na estação.
 *
 * Os critérios da parte continuam valendo dentro do modal: o estado tem
 * **rótulo textual** junto do ícone, e bloqueio nunca aparece sem o motivo
 * escrito por `cc-lock-reason`. Bloqueada também abre — é justamente ali que o
 * aluno lê por que ainda não liberou.
 *
 * O nó não decide nada: `availability` e `lockReason` chegam prontos da API.
 */
@Component({
  selector: 'cc-lesson-path-node',
  imports: [
    RouterLink,
    Button,
    Dialog,
    StarRatingComponent,
    CountdownComponent,
    LockReasonComponent,
    DateTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lesson-path-node.html',
  styleUrl: './lesson-path-node.scss',
})
export class LessonPathNodeComponent {
  readonly item = input.required<LessonTrackItem>();
  readonly roomId = input.required<string>();
  readonly starting = input(false);

  /** "Começar" / "Tentar novamente" / "Refazer". */
  readonly startRequested = output<LessonTrackItem>();
  /** "Continuar" numa tentativa em andamento. */
  readonly resumeRequested = output<string>();

  protected readonly detailsVisible = signal(false);

  protected readonly state = computed<NodeState>(() => STATES[this.item().availability]);

  protected readonly isLocked = computed(() => this.item().availability === 'LOCKED');

  /**
   * O nó que o aluno deve tocar agora. Ganha destaque e animação, como o
   * "próximo passo" da trilha.
   */
  protected readonly isCurrent = computed(() => {
    const availability = this.item().availability;
    return availability === 'AVAILABLE' || availability === 'IN_PROGRESS';
  });

  /** Texto do botão principal, conforme a situação. */
  protected readonly actionLabel = computed(() => {
    switch (this.item().availability) {
      case 'IN_PROGRESS':
        return 'Continuar';
      case 'PASSED':
        return 'Refazer';
      case 'FAILED':
        return 'Tentar novamente';
      default:
        return 'Começar';
    }
  });

  /** Descrição do círculo para leitores de tela: ordem, título e estado. */
  protected readonly nodeLabel = computed(
    () => `Lição ${this.item().order}: ${this.item().title} — ${this.state().label}`,
  );

  /** Tentativas restantes por extenso — `null` é "sem limite", nunca um número. */
  protected readonly attemptsLabel = computed(() => {
    const { attemptsUsed, attemptsRemaining, maxAttempts } = this.item().rules;
    if (maxAttempts === null || attemptsRemaining === null) {
      return `${attemptsUsed} ${attemptsUsed === 1 ? 'tentativa feita' : 'tentativas feitas'} · sem limite`;
    }
    return `${attemptsUsed} de ${maxAttempts} ${maxAttempts === 1 ? 'tentativa' : 'tentativas'} · restam ${attemptsRemaining}`;
  });

  protected open(): void {
    this.detailsVisible.set(true);
  }

  protected onPrimaryAction(): void {
    // Fecha antes de acionar: quem confirma as regras é o diálogo do
    // `AttemptLauncher`, e dois modais empilhados prendem o foco.
    this.detailsVisible.set(false);

    const item = this.item();
    if (item.activeAttemptId) {
      this.resumeRequested.emit(item.activeAttemptId);
      return;
    }
    this.startRequested.emit(item);
  }
}

const STATES: Record<LessonAvailability, NodeState> = {
  AVAILABLE: { label: 'Disponível', icon: 'pi pi-play', modifier: 'available' },
  IN_PROGRESS: { label: 'Em andamento', icon: 'pi pi-hourglass', modifier: 'progress' },
  PASSED: { label: 'Aprovada', icon: 'pi pi-check', modifier: 'passed' },
  FAILED: { label: 'Não aprovada', icon: 'pi pi-replay', modifier: 'failed' },
  LOCKED: { label: 'Bloqueada', icon: 'pi pi-lock', modifier: 'locked' },
};
