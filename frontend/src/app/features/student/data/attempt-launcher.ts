import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

import { ApiError } from '../../../core/api/problem-details';
import { NotificationService } from '../../../core/notifications/notification.service';
import { formatMinutes, formatPercent } from '../../../core/util/format';
import { newIdempotencyKey } from '../../../core/util/idempotency';
import { createSubmitGuard } from '../../../core/util/submitting';
import type { LessonRules } from '../models/lesson-track';
import { AttemptService } from './attempt.service';

/** O que a tela precisa informar para iniciar uma tentativa. */
export interface AttemptLaunchRequest {
  assignmentId: string;
  title: string;
  rules: LessonRules;
  /** Quando existe tentativa ativa, o aluno só retoma — nada é criado. */
  activeAttemptId: string | null;
}

/**
 * Início de tentativa — Parte 4, §6.2, passos 1 e 2.
 *
 * Existe como serviço, e não como método de página, porque a trilha e a tela da
 * lição oferecem o mesmo botão. Duplicar o fluxo significaria duplicar também o
 * cuidado com a chave de idempotência, que é justamente o que não pode variar.
 *
 * **A chave é gerada uma vez por intenção** — ao abrir o diálogo de confirmação
 * — e guardada por `assignmentId`. Repetições da mesma intenção (o aluno clica
 * de novo depois de uma falha de rede) reenviam a mesma chave, e a API devolve
 * a tentativa que já criou em vez de criar outra. Depois de um início
 * bem-sucedido a chave é descartada: a próxima vez que o aluno começar aquela
 * lição é uma intenção nova e merece uma tentativa nova.
 */
@Injectable({ providedIn: 'root' })
export class AttemptLauncher {
  private readonly service = inject(AttemptService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  private readonly guard = createSubmitGuard();
  private readonly keys = new Map<string, string>();

  /** Ligado aos botões "Começar"/"Tentar novamente" — bloqueia duplo envio. */
  readonly starting = this.guard.submitting;

  /** Retoma a tentativa em andamento, sem criar nada. */
  async resume(attemptId: string): Promise<void> {
    await this.router.navigate(['/aluno/tentativas', attemptId]);
  }

  /**
   * Mostra as regras, confirma e inicia.
   *
   * O diálogo é obrigatório: a §6.3 da Parte 4 exige avisar que a resposta não
   * poderá ser alterada **antes** de a tentativa começar.
   */
  async start(request: AttemptLaunchRequest): Promise<void> {
    if (request.activeAttemptId) {
      await this.resume(request.activeAttemptId);
      return;
    }

    const key = this.keyFor(request.assignmentId);

    const confirmed = await this.notify.confirm({
      header: `Começar "${request.title}"?`,
      message: rulesMessage(request.rules),
      acceptLabel: 'Começar agora',
      rejectLabel: 'Voltar',
      icon: 'pi pi-play-circle',
    });

    if (!confirmed) {
      return;
    }

    await this.guard.run(async () => {
      try {
        const attempt = await this.service.start(request.assignmentId, key);
        this.keys.delete(request.assignmentId);
        await this.router.navigate(['/aluno/tentativas', attempt.attemptId]);
      } catch (error) {
        // A chave permanece guardada: se o aluno tentar de novo, é a mesma
        // intenção e precisa da mesma chave.
        this.notify.error(
          error instanceof ApiError ? error : 'Não foi possível iniciar a tentativa.',
          'Tentativa não iniciada',
        );
      }
    });
  }

  private keyFor(assignmentId: string): string {
    const existing = this.keys.get(assignmentId);
    if (existing) {
      return existing;
    }
    const key = newIdempotencyKey();
    this.keys.set(assignmentId, key);
    return key;
  }
}

/**
 * Texto das regras.
 *
 * Sem tempo limite ou sem limite de tentativas viram a palavra "sem limite" —
 * nunca um número inventado (Parte 4, §5.2).
 */
function rulesMessage(rules: LessonRules): string {
  const attempts =
    rules.attemptsRemaining === null
      ? 'sem limite'
      : `${rules.attemptsRemaining} de ${rules.maxAttempts ?? rules.attemptsRemaining}`;

  return [
    `Tempo: ${formatMinutes(rules.timeLimitMinutes)}.`,
    `Questões: ${rules.questionCount}.`,
    `Tentativas restantes: ${attempts}.`,
    `Nota mínima para aprovação: ${formatPercent(rules.passingScorePercent)}.`,
    'Cada resposta é definitiva: depois de confirmada, não poderá ser alterada.',
  ].join(' ');
}
