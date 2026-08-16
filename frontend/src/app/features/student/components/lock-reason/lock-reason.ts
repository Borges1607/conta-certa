import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatDateTime } from '../../../../core/util/format';
import type { LessonLockReason } from '../../models/lesson-track';

/**
 * Motivo do bloqueio de uma lição — Parte 4, §5.1.
 *
 * O critério de aceite da parte exige que "todo estado de bloqueio da trilha
 * tenha texto explicativo próprio". Cadeado sozinho não explica nada; o aluno
 * precisa saber se falta aprovar a lição anterior, se a data ainda não chegou
 * ou se o prazo passou.
 *
 * **O motivo vem da API.** Este componente só traduz o código para português e
 * costura a data quando ela existe — nenhuma comparação de tempo acontece aqui.
 */
@Component({
  selector: 'cc-lock-reason',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="lock" role="note">
      <i class="pi pi-lock" aria-hidden="true"></i>
      <span>{{ text() }}</span>
    </p>
  `,
  styleUrl: './lock-reason.scss',
})
export class LockReasonComponent {
  readonly reason = input.required<LessonLockReason | null>();
  readonly availableFrom = input<string | null>(null);
  readonly dueAt = input<string | null>(null);

  protected readonly text = computed(() => {
    switch (this.reason()) {
      case 'PREREQUISITE_NOT_PASSED':
        return 'Você precisa ser aprovado na lição anterior para liberar esta.';
      case 'NOT_YET_AVAILABLE': {
        const from = this.availableFrom();
        return from
          ? `Esta lição abre em ${formatDateTime(from)}.`
          : 'Esta lição ainda não está disponível.';
      }
      case 'DUE_DATE_PASSED': {
        const due = this.dueAt();
        return due
          ? `O prazo terminou em ${formatDateTime(due)}.`
          : 'O prazo desta lição terminou.';
      }
      case 'NO_ATTEMPTS_LEFT':
        return 'Você já usou todas as suas tentativas nesta lição. Fale com seu professor se precisar de mais uma.';
      case 'NOT_PUBLISHED':
        return 'Seu professor ainda não publicou este conteúdo.';
      default:
        return 'Esta lição está bloqueada no momento.';
    }
  });
}
