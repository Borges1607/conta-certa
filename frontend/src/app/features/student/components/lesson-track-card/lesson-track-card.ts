import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';

import { CountdownComponent } from '../../../../shared/components/countdown/countdown';
import { StarRatingComponent } from '../../../../shared/components/star-rating/star-rating';
import { DateTimePipe } from '../../../../shared/pipes/format.pipes';
import type { LessonAvailability, LessonTrackItem } from '../../models/lesson-track';
import { LockReasonComponent } from '../lock-reason/lock-reason';

interface StateBadge {
  label: string;
  icon: string;
  modifier: string;
}

/**
 * Cartão de uma lição da trilha — Parte 4, §5.1.
 *
 * Cada estado tem **cor, ícone e rótulo textual**. Nenhum é comunicado só por
 * cor, e "bloqueada" nunca aparece sem explicação: o `cc-lock-reason` traduz o
 * código que a API mandou.
 *
 * O cartão não decide nada. `availability` e `lockReason` chegam prontos; aqui
 * não existe comparação de data, contagem de tentativas nem verificação de
 * pré-requisito.
 */
@Component({
  selector: 'cc-lesson-track-card',
  imports: [RouterLink, Button, StarRatingComponent, CountdownComponent, LockReasonComponent, DateTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lesson-track-card.html',
  styleUrl: './lesson-track-card.scss',
})
export class LessonTrackCardComponent {
  readonly item = input.required<LessonTrackItem>();
  readonly roomId = input.required<string>();
  readonly starting = input(false);

  /** "Começar" / "Tentar novamente" / "Refazer". */
  readonly startRequested = output<LessonTrackItem>();
  /** "Continuar" numa tentativa em andamento. */
  readonly resumeRequested = output<string>();

  protected readonly badge = computed<StateBadge>(() => BADGES[this.item().availability]);

  protected readonly isLocked = computed(() => this.item().availability === 'LOCKED');

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

  /** Tentativas restantes por extenso — `null` é "sem limite", nunca um número. */
  protected readonly attemptsLabel = computed(() => {
    const { attemptsUsed, attemptsRemaining, maxAttempts } = this.item().rules;
    if (maxAttempts === null || attemptsRemaining === null) {
      return `${attemptsUsed} ${attemptsUsed === 1 ? 'tentativa feita' : 'tentativas feitas'} · sem limite`;
    }
    return `${attemptsUsed} de ${maxAttempts} ${maxAttempts === 1 ? 'tentativa' : 'tentativas'} · restam ${attemptsRemaining}`;
  });

  protected onPrimaryAction(): void {
    const item = this.item();
    if (item.activeAttemptId) {
      this.resumeRequested.emit(item.activeAttemptId);
      return;
    }
    this.startRequested.emit(item);
  }
}

const BADGES: Record<LessonAvailability, StateBadge> = {
  AVAILABLE: { label: 'Disponível', icon: 'pi pi-play-circle', modifier: 'available' },
  IN_PROGRESS: { label: 'Em andamento', icon: 'pi pi-hourglass', modifier: 'progress' },
  PASSED: { label: 'Aprovada', icon: 'pi pi-check-circle', modifier: 'passed' },
  FAILED: { label: 'Não aprovada', icon: 'pi pi-times-circle', modifier: 'failed' },
  LOCKED: { label: 'Bloqueada', icon: 'pi pi-lock', modifier: 'locked' },
};
