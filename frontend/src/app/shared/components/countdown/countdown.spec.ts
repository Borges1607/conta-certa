import {
  ChangeDetectionStrategy,
  Component,
  provideZonelessChangeDetection,
  signal,
} from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ServerClock } from '../../../core/util/server-clock';
import { CountdownComponent } from './countdown';

@Component({
  imports: [CountdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<cc-countdown [expiresAt]="expiresAt()" (expired)="expiredCount = expiredCount + 1" />`,
})
class HostComponent {
  readonly expiresAt = signal(new Date(Date.now() + 600_000).toISOString());
  expiredCount = 0;
}

/**
 * Protege o critério da §11 da spec: "cronômetros usam `expiresAt` do servidor
 * e sobrevivem a recarga/fechamento da página".
 */
describe('CountdownComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  const create = async () => {
    fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  };

  const text = () => (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  it('mostra o tempo restante calculado a partir de expiresAt', async () => {
    await create();
    // 10 minutos, com folga de um segundo para o tempo de execução do teste.
    expect(text()).toMatch(/^(10:00|09:59)$/);
  });

  it('aplica o desvio do relógio do servidor', async () => {
    const clock = TestBed.inject(ServerClock);
    const localNow = Date.now();
    // Servidor 3 minutos à frente do relógio local.
    clock.registerResponse(new Date(localNow + 180_000).toUTCString(), localNow);

    fixture = TestBed.createComponent(HostComponent);
    // expiresAt a 10 minutos do relógio do servidor.
    fixture.componentInstance.expiresAt.set(new Date(localNow + 180_000 + 600_000).toISOString());
    await fixture.whenStable();

    // Sem a correção mostraria 13:00.
    expect(text()).toMatch(/^(10:00|09:59)$/);
  });

  it('sobrevive à recarga: um componente novo mostra o tempo certo', async () => {
    const expiresAt = new Date(Date.now() + 300_000).toISOString();

    fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.expiresAt.set(expiresAt);
    await fixture.whenStable();
    expect(text()).toMatch(/^(05:00|04:59)$/);

    // Simula a recarga da página: instância descartada, componente recriado
    // com o mesmo instante absoluto vindo da API.
    fixture.destroy();
    fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.expiresAt.set(expiresAt);
    await fixture.whenStable();

    expect(text()).toMatch(/^(05:00|04:59)$/);
  });

  it('emite expired uma única vez quando já passou do prazo', async () => {
    fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.expiresAt.set(new Date(Date.now() - 60_000).toISOString());
    await fixture.whenStable();

    expect(text()).toBe('00:00');
    expect(fixture.componentInstance.expiredCount).toBe(1);

    // Passar mais tempo não emite de novo.
    await new Promise((resolve) => setTimeout(resolve, 1100));
    await fixture.whenStable();
    expect(fixture.componentInstance.expiredCount).toBe(1);
  });

  it('marca o nível de urgência abaixo de 1 minuto', async () => {
    fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.expiresAt.set(new Date(Date.now() + 30_000).toISOString());
    await fixture.whenStable();

    const el = (fixture.nativeElement as HTMLElement).querySelector('.countdown');
    expect(el?.classList.contains('countdown--danger')).toBe(true);
    expect(el?.getAttribute('aria-live')).toBe('assertive');
  });

  it('marca o nível de atenção abaixo de 5 minutos', async () => {
    fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.expiresAt.set(new Date(Date.now() + 240_000).toISOString());
    await fixture.whenStable();

    const el = (fixture.nativeElement as HTMLElement).querySelector('.countdown');
    expect(el?.classList.contains('countdown--warning')).toBe(true);
  });
});
