import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ServerClock } from './server-clock';

/**
 * Protege o critério da §11 da spec: "cronômetros usam `expiresAt` do servidor".
 * O relógio da máquina do aluno pode estar errado — de propósito, inclusive.
 */
describe('ServerClock', () => {
  let clock: ServerClock;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    clock = TestBed.inject(ServerClock);
  });

  it('começa sem medição e sem desvio', () => {
    expect(clock.hasMeasurement()).toBe(false);
    expect(clock.skew()).toBe(0);
  });

  it('mede desvio positivo quando o relógio local está atrasado', () => {
    const localNow = Date.now();
    const serverNow = new Date(localNow + 180_000); // servidor 3 min à frente

    clock.registerResponse(serverNow.toUTCString(), localNow);

    expect(clock.hasMeasurement()).toBe(true);
    // Tolerância de 1s: o header `Date` tem resolução de segundos.
    expect(Math.abs(clock.skew() - 180_000)).toBeLessThan(1500);
  });

  it('aplica o desvio ao calcular o tempo restante', () => {
    const localNow = Date.now();
    // O servidor está 3 minutos à frente do relógio local.
    clock.registerResponse(new Date(localNow + 180_000).toUTCString(), localNow);

    // expiresAt a 10 minutos do relógio *do servidor*.
    const expiresAt = new Date(localNow + 180_000 + 600_000).toISOString();

    const restante = clock.remainingMs(expiresAt);

    // Sem correção daria 13 minutos. Com correção, 10.
    expect(Math.abs(restante - 600_000)).toBeLessThan(2000);
  });

  it('nunca devolve tempo negativo', () => {
    const passado = new Date(Date.now() - 60_000).toISOString();
    expect(clock.remainingMs(passado)).toBe(0);
    expect(clock.hasExpired(passado)).toBe(true);
  });

  it('ignora header ausente ou inválido', () => {
    clock.registerResponse(null, Date.now());
    clock.registerResponse('não é uma data', Date.now());
    expect(clock.hasMeasurement()).toBe(false);
  });

  it('descarta amostra com ida e volta absurda', () => {
    // Aba suspensa no meio da requisição: o RTT medido não vale nada.
    clock.registerResponse(new Date().toUTCString(), Date.now() - 300_000);
    expect(clock.hasMeasurement()).toBe(false);
  });

  it('trata expiresAt inválido como expirado', () => {
    expect(clock.remainingMs('nada disso')).toBe(0);
  });
});
