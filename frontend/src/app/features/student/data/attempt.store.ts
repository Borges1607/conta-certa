import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ApiError } from '../../../core/api/problem-details';
import { NotificationService } from '../../../core/notifications/notification.service';
import { createSubmitGuard } from '../../../core/util/submitting';
import { createPageState } from '../../../shared/forms/page-state';
import type {
  AnswerPayload,
  AttemptQuestion,
  RecordedAnswer,
} from '../models/attempt-question';
import { AttemptService } from './attempt.service';

/**
 * Estado de uma tentativa em andamento — Parte 4, §6.
 *
 * Três garantias moram aqui, e nenhuma delas depende de o componente se
 * comportar bem:
 *
 * 1. **Sigilo do gabarito.** Este arquivo importa `attempt-question.ts` e nada
 *    de `attempt-result.ts`. O que o `AttemptService` devolve ao registrar uma
 *    resposta já vem sem `correct`, então não existe valor de correção para
 *    guardar — nem por engano.
 * 2. **Tempo do servidor.** Não há relógio aqui. `expiresAt` é repassado ao
 *    `cc-countdown`, que calcula tudo a partir do `ServerClock`. O store só
 *    reage ao evento de expiração.
 * 3. **Imutabilidade.** Uma questão já registrada em `answered` nunca é
 *    reenviada; a checagem é feita antes de qualquer chamada.
 *
 * Fornecido pelo componente da tentativa, não em `root`: cada tentativa é um
 * estado novo, e sair da tela apaga tudo.
 */
@Injectable()
export class AttemptStore {
  private readonly service = inject(AttemptService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  private readonly answerGuard = createSubmitGuard();
  private readonly submitGuard = createSubmitGuard();

  private attemptId = '';
  /** Trava do envio automático: o cronômetro só pode disparar `submit` uma vez. */
  private autoSubmitted = false;

  /** Ciclo de carga da hidratação — `GET /student/attempts/{attemptId}`. */
  readonly page = createPageState(() => this.service.get(this.attemptId));

  private readonly answeredSignal = signal<ReadonlyMap<string, RecordedAnswer>>(new Map());
  private readonly indexSignal = signal(0);
  private readonly leavingSignal = signal(false);

  readonly attempt = this.page.data;
  readonly isLoading = this.page.isLoading;
  readonly error = this.page.error;

  readonly questions = computed<AttemptQuestion[]>(() => this.attempt()?.questions ?? []);
  readonly total = computed(() => this.questions().length);
  readonly currentIndex = this.indexSignal.asReadonly();
  readonly currentQuestion = computed<AttemptQuestion | null>(
    () => this.questions()[this.indexSignal()] ?? null,
  );

  /** Questões respondidas. Marcadas como *respondidas*, jamais como certas. */
  readonly answered = this.answeredSignal.asReadonly();
  readonly answeredCount = computed(() => this.answeredSignal().size);
  readonly allAnswered = computed(
    () => this.total() > 0 && this.answeredCount() === this.total(),
  );

  /** Resposta já registrada da questão atual, para reexibir em leitura. */
  readonly currentRecorded = computed<RecordedAnswer | null>(() => {
    const question = this.currentQuestion();
    return question
      ? (this.answeredSignal().get(question.questionSnapshotId) ?? null)
      : null;
  });

  readonly currentIsAnswered = computed(() => this.currentRecorded() !== null);

  /** `null` quando a atribuição não tem tempo limite: nenhum cronômetro é exibido. */
  readonly expiresAt = computed(() => this.attempt()?.expiresAt ?? null);

  readonly answering = this.answerGuard.submitting;
  readonly submitting = this.submitGuard.submitting;
  /** Verdadeiro enquanto navegamos para o resultado — desliga o guard de saída. */
  readonly leaving = this.leavingSignal.asReadonly();

  /**
   * Hidrata a tela. Chamado a cada entrada, inclusive recarga da página.
   *
   * O estado autoritativo está no servidor: horários, questões sorteadas e
   * respostas já registradas voltam daqui, e é por isso que fechar e reabrir a
   * aba não perde nada nem reinicia cronômetro.
   */
  async init(attemptId: string): Promise<void> {
    this.attemptId = attemptId;
    this.autoSubmitted = false;
    this.leavingSignal.set(false);
    this.answeredSignal.set(new Map());
    this.indexSignal.set(0);

    await this.page.load();

    const attempt = this.attempt();
    if (!attempt) {
      return;
    }

    const answered = new Map<string, RecordedAnswer>(
      attempt.answers.map((answer) => [answer.questionSnapshotId, answer]),
    );
    this.answeredSignal.set(answered);

    // Tentativa já encerrada — inclusive expirada pelo próprio servidor: a tela
    // não fica travada, vai direto ao resultado (Parte 4, §6.4).
    if (attempt.status !== 'IN_PROGRESS') {
      await this.goToResult(attempt.status === 'EXPIRED');
      return;
    }

    this.indexSignal.set(firstUnansweredIndex(attempt.questions, answered));
  }

  goTo(index: number): void {
    if (index >= 0 && index < this.total()) {
      this.indexSignal.set(index);
    }
  }

  previous(): void {
    this.goTo(this.indexSignal() - 1);
  }

  next(): void {
    this.goTo(this.indexSignal() + 1);
  }

  /**
   * Registra a resposta da questão atual — passo 4 do ciclo.
   *
   * A resposta é imutável dentro da tentativa: uma questão já respondida nunca
   * chega a produzir requisição.
   */
  async confirmCurrent(payload: AnswerPayload): Promise<void> {
    const question = this.currentQuestion();
    if (!question || this.answeredSignal().has(question.questionSnapshotId)) {
      return;
    }

    const confirmed = await this.notify.confirm({
      header: 'Confirmar resposta?',
      message: 'Sua resposta não poderá ser alterada.',
      acceptLabel: 'Confirmar resposta',
      rejectLabel: 'Revisar',
      icon: 'pi pi-check-circle',
    });

    if (!confirmed) {
      return;
    }

    await this.answerGuard.run(async () => {
      try {
        const recorded = await this.service.answer(
          this.attemptId,
          question.questionSnapshotId,
          payload,
        );

        // `recorded` é um `RecordedAnswer`: não existe `correct` para guardar.
        // A tela passa a marcar a questão como respondida e nada mais.
        this.answeredSignal.update((current) => {
          const updated = new Map(current);
          updated.set(recorded.questionSnapshotId, recorded);
          return updated;
        });

        this.advanceAfterAnswer();
      } catch (error) {
        await this.handleAnswerFailure(error);
      }
    });
  }

  /** Finalização explícita pelo aluno — passo 5 do ciclo. */
  async submit(): Promise<void> {
    const confirmed = await this.notify.confirm({
      header: 'Finalizar tentativa?',
      message: this.allAnswered()
        ? 'Você respondeu todas as questões. Depois de finalizar, não é possível voltar.'
        : 'Há questões sem resposta, e questões sem resposta contam como incorretas. Deseja finalizar mesmo assim?',
      acceptLabel: 'Finalizar',
      rejectLabel: 'Continuar respondendo',
      icon: 'pi pi-flag',
    });

    if (confirmed) {
      await this.finish(false);
    }
  }

  /**
   * Disparado pelo `cc-countdown` ao chegar a zero.
   *
   * Uma única vez, garantido por `autoSubmitted`. Se este envio falhar, não tem
   * problema: **o servidor expira e corrige a tentativa por conta própria**, e
   * `finish` cai na leitura do resultado.
   */
  async expire(): Promise<void> {
    if (this.autoSubmitted) {
      return;
    }
    this.autoSubmitted = true;

    this.notify.info(
      'Seu tempo terminou',
      'Estamos finalizando sua tentativa e preparando o resultado.',
    );

    await this.finish(true);
  }

  /**
   * Confirmação de saída — `CanDeactivate` da Parte 4, §6.5.
   *
   * O aviso é honesto sobre o que acontece: o tempo continua correndo no
   * servidor, então sair não pausa nada.
   */
  async canLeave(): Promise<boolean> {
    if (this.leavingSignal()) {
      return true;
    }

    const attempt = this.attempt();
    if (!attempt || attempt.status !== 'IN_PROGRESS') {
      return true;
    }

    return this.notify.confirm({
      header: 'Sair da tentativa?',
      message:
        'O tempo continua correndo e a tentativa permanece em andamento. As respostas já confirmadas continuam registradas.',
      acceptLabel: 'Sair mesmo assim',
      rejectLabel: 'Continuar na tentativa',
      icon: 'pi pi-exclamation-triangle',
    });
  }

  private async finish(expiredByTime: boolean): Promise<void> {
    await this.submitGuard.run(async () => {
      try {
        await this.service.submit(this.attemptId);
        await this.goToResult(expiredByTime);
      } catch (error) {
        await this.handleSubmitFailure(error, expiredByTime);
      }
    });
  }

  /**
   * O `submit` falhou. A tela **não trava**.
   *
   * `410` e conflito de estado significam que o servidor já encerrou a
   * tentativa — encerramento normal. Em qualquer outra falha, ainda vale
   * perguntar pelo resultado: se ele existe, a correção do servidor aconteceu
   * e o aluno segue para a tela de resultado do mesmo jeito.
   */
  private async handleSubmitFailure(error: unknown, _expiredByTime: boolean): Promise<void> {
    if (error instanceof ApiError && (error.isGone || error.status === 409)) {
      await this.goToResult(true);
      return;
    }

    if (await this.resultExists()) {
      await this.goToResult(true);
      return;
    }

    this.notify.error(
      error instanceof ApiError ? error : 'Não foi possível finalizar a tentativa.',
      'Tentativa não finalizada',
    );
    // Libera o envio automático para o cronômetro poder tentar de novo caso o
    // aluno recupere a conexão sem sair da tela.
    this.autoSubmitted = false;
  }

  private async handleAnswerFailure(error: unknown): Promise<void> {
    if (error instanceof ApiError && (error.isGone || error.status === 409)) {
      this.notify.warn(
        'Tentativa encerrada',
        'Seu tempo terminou e a tentativa foi corrigida.',
      );
      await this.goToResult(true);
      return;
    }

    this.notify.error(
      error instanceof ApiError ? error : 'Não foi possível registrar sua resposta.',
      'Resposta não registrada',
    );
  }

  private async resultExists(): Promise<boolean> {
    try {
      await this.service.result(this.attemptId);
      return true;
    } catch {
      return false;
    }
  }

  private async goToResult(expired: boolean): Promise<void> {
    this.leavingSignal.set(true);
    await this.router.navigate(['/aluno/tentativas', this.attemptId, 'resultado'], {
      queryParams: expired ? { expirado: '1' } : {},
      replaceUrl: true,
    });
  }

  /** Depois de confirmar, segue para a próxima questão ainda sem resposta. */
  private advanceAfterAnswer(): void {
    const next = firstUnansweredIndex(this.questions(), this.answeredSignal());
    this.indexSignal.set(next === -1 ? this.indexSignal() : next);
  }
}

/**
 * Índice da primeira questão sem resposta; `-1` quando todas já têm.
 *
 * É o que faz a retomada cair no lugar certo depois de uma recarga.
 */
function firstUnansweredIndex(
  questions: readonly AttemptQuestion[],
  answered: ReadonlyMap<string, RecordedAnswer>,
): number {
  return questions.findIndex((question) => !answered.has(question.questionSnapshotId));
}
