import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';

import { ApiClient } from '../../../core/api/api-client';
import type {
  AnswerPayload,
  AttemptDetail,
  AttemptStartResponse,
  RecordedAnswer,
} from '../models/attempt-question';
import type { AttemptResult } from '../models/attempt-result';

/**
 * Resposta crua de `PUT /student/attempts/{id}/answers/{questionSnapshotId}`.
 *
 * **Este tipo não é exportado, de propósito.** A API devolve `correct` junto do
 * registro (§6.3 da spec de integração), e é exatamente aqui — na borda HTTP —
 * que esse campo morre. Nenhum store, componente ou template do aluno chega a
 * enxergá-lo durante a tentativa (Parte 4, §6.1).
 */
interface AnswerAckWire {
  questionSnapshotId: string;
  answeredAt: string;
  correct: boolean;
}

/** Ciclo da tentativa — §6.3 da spec de integração e Parte 4, §6.2. */
@Injectable({ providedIn: 'root' })
export class AttemptService {
  private readonly api = inject(ApiClient);

  /**
   * Inicia uma tentativa ou devolve a ativa.
   *
   * `idempotencyKey` é obrigatório aqui de propósito: sem ele, dois cliques em
   * "Começar" poderiam criar duas tentativas. Quem gera e reaproveita a chave é
   * o `AttemptLauncher`.
   */
  start(assignmentId: string, idempotencyKey: string): Promise<AttemptStartResponse> {
    return firstValueFrom(
      this.api.post<AttemptStartResponse>(
        `/student/room-lessons/${assignmentId}/attempts`,
        {},
        { idempotencyKey },
      ),
    );
  }

  /**
   * Hidrata a tela em qualquer entrada, inclusive recarga: status, horários,
   * questões sorteadas e respostas já registradas.
   */
  get(attemptId: string): Promise<AttemptDetail> {
    return firstValueFrom(this.api.get<AttemptDetail>(`/student/attempts/${attemptId}`));
  }

  /**
   * Registra uma resposta. Devolve apenas o comprovante do registro.
   *
   * A conversão abaixo é literal e deliberada: os campos do retorno são
   * escritos um a um, e `correct` simplesmente não é copiado. Não é um
   * `delete`, não é um spread com omissão — é uma construção nova, que não tem
   * como vazar o gabarito nem se a API acrescentar campos no futuro.
   */
  answer(
    attemptId: string,
    questionSnapshotId: string,
    payload: AnswerPayload,
  ): Promise<RecordedAnswer> {
    return firstValueFrom(
      this.api
        .put<AnswerAckWire>(
          `/student/attempts/${attemptId}/answers/${questionSnapshotId}`,
          payload,
        )
        .pipe(
          map(
            (ack): RecordedAnswer => ({
              questionSnapshotId: ack.questionSnapshotId,
              answeredAt: ack.answeredAt,
              answer: payload,
            }),
          ),
        ),
    );
  }

  /**
   * Finaliza explicitamente.
   *
   * O corpo da resposta é ignorado: a tela de resultado sempre consulta
   * `result()`, o que mantém um único caminho de leitura do gabarito.
   */
  async submit(attemptId: string): Promise<void> {
    await firstValueFrom(this.api.post<unknown>(`/student/attempts/${attemptId}/submit`));
  }

  /** Resultado corrigido. Único endpoint que devolve gabarito e explicação. */
  result(attemptId: string): Promise<AttemptResult> {
    return firstValueFrom(
      this.api.get<AttemptResult>(`/student/attempts/${attemptId}/result`),
    );
  }
}
