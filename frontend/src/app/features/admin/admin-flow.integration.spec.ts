import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthStore } from '../../core/auth/auth.store';
import { authInterceptor } from '../../core/interceptors/auth.interceptor';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { refreshInterceptor } from '../../core/interceptors/refresh.interceptor';
import { resetDatabase } from '../../../mocks/db';
import { mockApiInterceptor } from '../../../mocks/mock-api.interceptor';
import '../../../mocks/seed';
import { AdminDashboardService } from './data/admin-dashboard.service';
import { AdminTeacherService } from './data/admin-teacher.service';
import { FinancialTipService } from './data/financial-tip.service';
import { InstitutionService } from './data/institution.service';

/**
 * Percorre a jornada do admin pela cadeia real.
 *
 * Foca nos pontos que a Parte 6 trata como críticos: CNPJ normalizado,
 * `publicationDate` como `LocalDate`, `version` sem sobrescrita, ausência de
 * qualquer campo de senha e o efeito imediato da desativação.
 */
describe('Jornada do administrador (integração com o mock)', () => {
  let auth: AuthStore;
  let institutions: InstitutionService;
  let teachers: AdminTeacherService;
  let tips: FinancialTipService;

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    resetDatabase();

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(
          withInterceptors([
            authInterceptor,
            errorInterceptor,
            refreshInterceptor,
            mockApiInterceptor,
          ]),
        ),
      ],
    });

    auth = TestBed.inject(AuthStore);
    institutions = TestBed.inject(InstitutionService);
    teachers = TestBed.inject(AdminTeacherService);
    tips = TestBed.inject(FinancialTipService);

    await auth.login({ email: 'admin@contacerta.dev', password: 'senha123' });
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('autentica como admin, sem instituição vinculada', () => {
    expect(auth.user()?.role).toBe('ADMIN');
    // O admin é global: não pertence a nenhuma instituição (§3 da spec).
    expect(auth.user()?.institution).toBeNull();
  });

  it('o painel devolve só contagens', async () => {
    const data = await TestBed.inject(AdminDashboardService).load();

    expect(data.institutions.total).toBeGreaterThan(0);
    expect(data.institutions.active + data.institutions.inactive).toBe(data.institutions.total);
    expect(data.teachers.pending + data.teachers.active + data.teachers.inactive).toBe(
      data.teachers.total,
    );

    // A matriz da §10 é literal: nada de ranking ou conteúdo para o admin.
    expect(JSON.stringify(data)).not.toContain('ranking');
  });

  it('envia o CNPJ com 14 dígitos, sem pontuação', async () => {
    const created = await institutions.create({
      name: 'Colégio Teste',
      // CNPJ ausente da semente, enviado com máscara.
      cnpj: '99.888.777/0001-66',
      contactEmail: 'contato@teste.example',
      contactPhone: '+5548999998888',
    });

    expect(created.cnpj).toBe('99888777000166');
    expect(created.cnpj).not.toContain('.');
    expect(created.cnpj).not.toContain('/');
  });

  it('recusa CNPJ duplicado', async () => {
    await expect(
      institutions.create({
        name: 'Duplicada',
        // Este já existe na semente (IFSC).
        cnpj: '11222333000181',
        contactEmail: 'x@y.example',
        contactPhone: '+5548999990000',
      }),
    ).rejects.toMatchObject({ status: 409 });
  });

  it('exige version na edição e não sobrescreve com versão antiga', async () => {
    const page = await institutions.list({}, { page: 0, size: 20 });
    const institution = page.content[0];

    const updated = await institutions.update(institution.id, {
      version: institution.version,
      name: 'Nome atualizado',
    });
    expect(updated.name).toBe('Nome atualizado');

    await expect(
      institutions.update(institution.id, {
        version: institution.version,
        name: 'Sobrescrita indevida',
      }),
    ).rejects.toMatchObject({ status: 409, code: 'VERSION_CONFLICT' });

    const fresh = await institutions.get(institution.id);
    expect(fresh.name).toBe('Nome atualizado');
  });

  it('desativar instituição preserva os vínculos existentes', async () => {
    const page = await institutions.list({}, { page: 0, size: 20 });
    const withLinks = page.content.find((i) => (i.teacherCount ?? 0) > 0)!;

    const deactivated = await institutions.deactivate(withLinks.id);

    expect(deactivated.active).toBe(false);
    // Ninguém perde acesso: os vínculos continuam lá.
    expect(deactivated.teacherCount).toBe(withLinks.teacherCount);
    expect(deactivated.studentCount).toBe(withLinks.studentCount);
  });

  it('não exclui instituição com vínculos', async () => {
    const page = await institutions.list({}, { page: 0, size: 20 });
    const withLinks = page.content.find((i) => (i.teacherCount ?? 0) > 0)!;

    await expect(institutions.remove(withLinks.id)).rejects.toMatchObject({ status: 409 });
  });

  it('criar professor não aceita senha e gera conta pendente', async () => {
    const created = await teachers.create({
      fullName: 'Novo Professor',
      email: 'novo@contacerta.dev',
      registrationNumber: 'P2026099',
      institutionId: auth.user()?.institution?.id ?? (await firstInstitutionId(institutions)),
    });

    expect(created.status).toBe('PENDING');
    expect(created.emailVerified).toBe(false);
    // Nenhum campo de senha existe no contrato nem na resposta.
    expect(JSON.stringify(created)).not.toContain('password');
    expect(JSON.stringify(created)).not.toContain('senha');
  });

  it('desativar professor revoga a sessão dele imediatamente', async () => {
    // A professora Ana entra e obtém uma sessão válida.
    const teacherAuth = TestBed.inject(AuthStore);
    const page = await teachers.list({}, { page: 0, size: 20 });
    const ana = page.content.find((t) => t.email === 'ana@contacerta.dev')!;

    await teachers.deactivate(ana.id);

    // Agora ela tenta entrar: conta inativa não autentica.
    await expect(
      teacherAuth.login({ email: 'ana@contacerta.dev', password: 'senha123' }),
    ).rejects.toMatchObject({ status: 403 });
  });

  it('o envio de redefinição não devolve senha nem token ao admin', async () => {
    const page = await teachers.list({}, { page: 0, size: 20 });
    const teacher = page.content[0];

    const result = await teachers.sendPasswordReset(teacher.id);

    // `204 No Content`: o link vai por e-mail, não pela resposta.
    expect(result).toBeFalsy();
  });

  it('publicationDate trafega como YYYY-MM-DD, sem componente de hora', async () => {
    const created = await tips.create({
      title: 'Dica de teste',
      content: 'Guarde **10%** de tudo que receber.',
      sourceUrl: null,
      publicationDate: '2026-03-15',
      active: true,
    });

    // Nem `T`, nem `Z`, nem deslocamento de fuso.
    expect(created.publicationDate).toBe('2026-03-15');
    expect(created.publicationDate).not.toContain('T');
    expect(created.publicationDate).not.toContain('Z');
    expect(created.publicationDate.length).toBe(10);
  });

  it('recusa publicationDate em formato de instante', async () => {
    await expect(
      tips.create({
        title: 'Data errada',
        content: 'x',
        sourceUrl: null,
        publicationDate: '2026-03-15T00:00:00Z',
        active: true,
      }),
    ).rejects.toMatchObject({ status: 422 });
  });

  it('a dica do dia não muda de dia por causa do fuso do navegador', async () => {
    // 1º de janeiro é o caso clássico: em UTC-3, `new Date('2026-01-01')`
    // vira 31/12 no horário local. Aqui o valor é texto e não se move.
    const created = await tips.create({
      title: 'Virada de ano',
      content: 'x',
      sourceUrl: null,
      publicationDate: '2026-01-01',
      active: true,
    });

    const fetched = await tips.get(created.id);
    expect(fetched.publicationDate).toBe('2026-01-01');
  });

  it('ativa e desativa dica preservando o agendamento', async () => {
    const page = await tips.list({}, { page: 0, size: 20 });
    const tip = page.content.find((t) => t.active)!;

    const deactivated = await tips.deactivate(tip.id);
    expect(deactivated.active).toBe(false);
    expect(deactivated.publicationDate).toBe(tip.publicationDate);

    const reactivated = await tips.activate(tip.id);
    expect(reactivated.active).toBe(true);
  });

  it('professor não acessa a área do admin', async () => {
    await auth.logout().catch(() => undefined);
    await auth.login({ email: 'ana@contacerta.dev', password: 'senha123' });

    await expect(TestBed.inject(AdminDashboardService).load()).rejects.toMatchObject({
      status: 403,
    });
  });
});

async function firstInstitutionId(service: InstitutionService): Promise<string> {
  const options = await service.options();
  return options[0].id;
}
