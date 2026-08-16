import { Injectable, signal } from '@angular/core';

/**
 * Relógio do servidor — Parte 1, §7.
 *
 * A spec de integração exige que cronômetros usem `expiresAt` do servidor,
 * sobrevivam a recarga e não inventem tempo local. O relógio da máquina do
 * aluno pode estar errado — de propósito, inclusive —, então medimos o desvio
 * contra o header `Date` das respostas e corrigimos toda leitura por ele.
 *
 * Nada aqui decide expiração: quem expira e corrige a tentativa é o servidor.
 * Isto serve só para a contagem exibida ser honesta.
 */
@Injectable({ providedIn: 'root' })
export class ServerClock {
  /** `tempoDoServidor - tempoLocal`, em milissegundos. */
  private readonly skewMs = signal(0);
  private readonly measured = signal(false);

  /** Verdadeiro depois da primeira resposta com header `Date`. */
  readonly hasMeasurement = this.measured.asReadonly();
  readonly skew = this.skewMs.asReadonly();

  /**
   * Registra uma medição a partir de uma resposta.
   *
   * O header `Date` marca o instante em que o servidor gerou a resposta, ou
   * seja, aproximadamente o meio do trajeto de ida e volta. Descontar metade
   * do RTT tira o viés da latência.
   */
  registerResponse(dateHeader: string | null, requestStartedAtMs: number): void {
    if (!dateHeader) {
      return;
    }

    const serverTime = Date.parse(dateHeader);
    if (Number.isNaN(serverTime)) {
      return;
    }

    const receivedAt = Date.now();
    const roundTrip = receivedAt - requestStartedAtMs;

    // Amostra absurda (relógio pulou, aba suspensa no meio da requisição):
    // descarta em vez de contaminar a medição.
    if (roundTrip < 0 || roundTrip > 60_000) {
      return;
    }

    const localAtServerTime = requestStartedAtMs + roundTrip / 2;
    const sample = serverTime - localAtServerTime;

    // O header `Date` tem resolução de 1 segundo, então amostras individuais
    // são ruidosas. Suavizamos para não oscilar a cada requisição.
    this.skewMs.update((current) => (this.measured() ? current * 0.7 + sample * 0.3 : sample));
    this.measured.set(true);
  }

  /** Instante atual corrigido pelo desvio medido. */
  now(): number {
    return Date.now() + this.skewMs();
  }

  /**
   * Milissegundos restantes até `expiresAt`, nunca negativo.
   *
   * Recalcular a partir do instante absoluto — em vez de decrementar um
   * contador — é o que faz o cronômetro sobreviver a recarga, a aba suspensa e
   * a máquina hibernando.
   */
  remainingMs(expiresAt: string): number {
    const target = Date.parse(expiresAt);
    if (Number.isNaN(target)) {
      return 0;
    }
    return Math.max(0, target - this.now());
  }

  hasExpired(expiresAt: string): boolean {
    return this.remainingMs(expiresAt) <= 0;
  }
}
