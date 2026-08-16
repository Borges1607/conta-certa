import { db, type DbFinancialTip, type DbInstitution, type DbUser } from '../db';
import {
  NO_CONTENT,
  conflict,
  notFound,
  unprocessable,
  versionConflict,
  type MockContext,
  type MockRoute,
} from '../router';
import {
  isoIn,
  matchesSearch,
  mutate,
  newId,
  nowIso,
  onlyDigits,
  paginate,
  requireRole,
  toInstitutionSummary,
} from '../support';

/**
 * Handlers do administrador — §8 da spec de integração.
 *
 * Três regras estruturais são aplicadas aqui, do lado do servidor:
 *
 * - Criar professor **nunca** aceita senha; gera conta `PENDING` e um convite.
 * - Desativar professor revoga todas as sessões na hora.
 * - Instituição com vínculo não é apagada, só desativada.
 */

function admin(context: MockContext): DbUser {
  return requireRole(context, 'ADMIN');
}

function body<T>(context: MockContext): T {
  return (context.body ?? {}) as T;
}

function checkVersion(current: number, sent: unknown): void {
  if (typeof sent !== 'number' || sent !== current) {
    throw versionConflict();
  }
}

function institutionOrFail(id: string): DbInstitution {
  const institution = db().institutions.find((i) => i.id === id);
  if (!institution) {
    throw notFound('Instituição não encontrada.');
  }
  return institution;
}

function teacherOrFail(id: string): DbUser {
  const teacher = db().users.find((u) => u.id === id && u.role === 'TEACHER');
  if (!teacher) {
    throw notFound('Professor não encontrado.');
  }
  return teacher;
}

function tipOrFail(id: string): DbFinancialTip {
  const tip = db().financialTips.find((t) => t.id === id);
  if (!tip) {
    throw notFound('Dica não encontrada.');
  }
  return tip;
}

function linkCounts(institutionId: string) {
  const users = db().users.filter((u) => u.institutionId === institutionId);
  return {
    teacherCount: users.filter((u) => u.role === 'TEACHER').length,
    studentCount: users.filter((u) => u.role === 'STUDENT').length,
  };
}

function toAdminInstitution(institution: DbInstitution) {
  return {
    ...toInstitutionSummary(institution),
    version: institution.version,
    createdAt: institution.createdAt,
    updatedAt: institution.createdAt,
    ...linkCounts(institution.id),
  };
}

function toAdminTeacher(teacher: DbUser) {
  const institution = teacher.institutionId
    ? db().institutions.find((i) => i.id === teacher.institutionId)
    : undefined;

  return {
    id: teacher.id,
    fullName: teacher.fullName,
    email: teacher.email,
    registrationNumber: teacher.registrationNumber,
    institution: institution ? toInstitutionSummary(institution) : null,
    status: teacher.status,
    emailVerified: teacher.emailVerified,
    version: teacher.version,
    createdAt: teacher.createdAt,
    updatedAt: teacher.createdAt,
    lastLoginAt: null,
  };
}

function toTip(tip: DbFinancialTip) {
  return {
    id: tip.id,
    title: tip.title,
    content: tip.content,
    sourceUrl: tip.sourceUrl,
    // LocalDate: sai como texto, sem conversão de fuso.
    publicationDate: tip.publicationDate,
    active: tip.active,
    version: tip.version,
  };
}

/** Revoga todas as sessões de um usuário — efeito imediato. */
function revokeSessions(userId: string): void {
  for (const session of db().sessions.filter((s) => s.userId === userId)) {
    session.revoked = true;
  }
}

export const adminRoutes: MockRoute[] = [
  {
    method: 'GET',
    path: '/admin/dashboard',
    handler: (context) => {
      admin(context);
      const institutions = db().institutions;
      const teachers = db().users.filter((u) => u.role === 'TEACHER');

      return {
        institutions: {
          total: institutions.length,
          active: institutions.filter((i) => i.active).length,
          inactive: institutions.filter((i) => !i.active).length,
        },
        teachers: {
          total: teachers.length,
          pending: teachers.filter((t) => t.status === 'PENDING').length,
          active: teachers.filter((t) => t.status === 'ACTIVE').length,
          inactive: teachers.filter((t) => t.status === 'INACTIVE').length,
        },
      };
    },
  },

  {
    method: 'GET',
    path: '/admin/institutions',
    handler: (context) => {
      admin(context);
      const search = context.query.get('search');
      const active = context.query.get('active');

      const rows = db()
        .institutions.filter((i) => matchesSearch(search, i.name, i.cnpj))
        .filter((i) => (active === null ? true : i.active === (active === 'true')))
        .map(toAdminInstitution);

      return paginate(rows, context);
    },
  },

  {
    method: 'POST',
    path: '/admin/institutions',
    handler: (context) => {
      admin(context);
      const payload = body<{
        name: string;
        cnpj: string;
        contactEmail: string;
        contactPhone: string;
      }>(context);

      const cnpj = onlyDigits(payload.cnpj ?? '');

      if (cnpj.length !== 14) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'cnpj', message: 'O CNPJ precisa ter 14 dígitos.' },
        ]);
      }
      if (db().institutions.some((i) => i.cnpj === cnpj)) {
        throw conflict('DUPLICATE_CNPJ', 'Já existe uma instituição com este CNPJ.');
      }

      return mutate(() => {
        const institution: DbInstitution = {
          id: newId('inst'),
          name: payload.name,
          // Armazenado normalizado, com 14 dígitos e sem pontuação.
          cnpj,
          contactEmail: payload.contactEmail,
          contactPhone: payload.contactPhone,
          active: true,
          version: 1,
          createdAt: nowIso(),
        };
        db().institutions.push(institution);
        return toAdminInstitution(institution);
      });
    },
  },

  {
    method: 'GET',
    path: '/admin/institutions/:institutionId',
    handler: (context) => {
      admin(context);
      return toAdminInstitution(institutionOrFail(context.params['institutionId']));
    },
  },

  {
    method: 'PATCH',
    path: '/admin/institutions/:institutionId',
    handler: (context) => {
      admin(context);
      const institution = institutionOrFail(context.params['institutionId']);
      const payload = body<Record<string, unknown>>(context);

      checkVersion(institution.version, payload['version']);

      if (typeof payload['cnpj'] === 'string') {
        const cnpj = onlyDigits(payload['cnpj']);
        if (cnpj.length !== 14) {
          throw unprocessable('Verifique os campos destacados.', [
            { field: 'cnpj', message: 'O CNPJ precisa ter 14 dígitos.' },
          ]);
        }
        if (db().institutions.some((i) => i.cnpj === cnpj && i.id !== institution.id)) {
          throw conflict('DUPLICATE_CNPJ', 'Já existe uma instituição com este CNPJ.');
        }
      }

      return mutate(() => {
        if (typeof payload['name'] === 'string') {
          institution.name = payload['name'];
        }
        if (typeof payload['cnpj'] === 'string') {
          institution.cnpj = onlyDigits(payload['cnpj']);
        }
        if (typeof payload['contactEmail'] === 'string') {
          institution.contactEmail = payload['contactEmail'];
        }
        if (typeof payload['contactPhone'] === 'string') {
          institution.contactPhone = payload['contactPhone'];
        }
        institution.version++;
        return toAdminInstitution(institution);
      });
    },
  },

  {
    method: 'POST',
    path: '/admin/institutions/:institutionId/activate',
    handler: (context) => {
      admin(context);
      const institution = institutionOrFail(context.params['institutionId']);
      return mutate(() => {
        institution.active = true;
        institution.version++;
        return toAdminInstitution(institution);
      });
    },
  },

  {
    method: 'POST',
    path: '/admin/institutions/:institutionId/deactivate',
    handler: (context) => {
      admin(context);
      const institution = institutionOrFail(context.params['institutionId']);
      return mutate(() => {
        // Desativar bloqueia **novos** vínculos; os existentes continuam.
        institution.active = false;
        institution.version++;
        return toAdminInstitution(institution);
      });
    },
  },

  {
    method: 'DELETE',
    path: '/admin/institutions/:institutionId',
    handler: (context) => {
      admin(context);
      const institution = institutionOrFail(context.params['institutionId']);
      const counts = linkCounts(institution.id);

      if (counts.teacherCount > 0 || counts.studentCount > 0) {
        throw conflict(
          'INSTITUTION_HAS_LINKS',
          'Esta instituição tem professores ou alunos vinculados. Desative-a em vez de excluir.',
        );
      }

      return mutate(() => {
        db().institutions = db().institutions.filter((i) => i.id !== institution.id);
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/admin/teachers',
    handler: (context) => {
      admin(context);
      const search = context.query.get('search');
      const status = context.query.get('status');
      const institutionId = context.query.get('institutionId');

      const rows = db()
        .users.filter((u) => u.role === 'TEACHER')
        .filter((u) => matchesSearch(search, u.fullName, u.email, u.registrationNumber))
        .filter((u) => (status ? u.status === status : true))
        .filter((u) => (institutionId ? u.institutionId === institutionId : true))
        .map(toAdminTeacher);

      return paginate(rows, context);
    },
  },

  {
    method: 'POST',
    path: '/admin/teachers',
    handler: (context) => {
      admin(context);
      const payload = body<{
        fullName: string;
        email: string;
        registrationNumber: string;
        institutionId: string;
      }>(context);

      if (db().users.some((u) => u.email.toLowerCase() === (payload.email ?? '').toLowerCase())) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'email', message: 'Este e-mail já está cadastrado.' },
        ]);
      }

      const institution = db().institutions.find((i) => i.id === payload.institutionId);
      if (!institution?.active) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'institutionId', message: 'Instituição inválida ou inativa.' },
        ]);
      }

      return mutate(() => {
        const teacher: DbUser = {
          id: newId('user'),
          role: 'TEACHER',
          // Conta nasce PENDENTE: quem define a senha é o próprio professor.
          status: 'PENDING',
          fullName: payload.fullName,
          email: payload.email,
          // Sem senha utilizável até o convite ser aceito.
          password: newId('unset'),
          registrationNumber: payload.registrationNumber,
          institutionId: payload.institutionId,
          emailVerified: false,
          mustChangePassword: false,
          version: 1,
          createdAt: nowIso(),
        };
        db().users.push(teacher);

        const token = newId('invite');
        db().actionTokens.push({
          token,
          type: 'TEACHER_INVITE',
          userId: teacher.id,
          expiresAt: isoIn(7 * 86_400_000),
          usedAt: null,
        });
        console.info(
          `[mock] Convite para ${teacher.email}: /convite-professor?token=${token}`,
        );

        return toAdminTeacher(teacher);
      });
    },
  },

  {
    method: 'GET',
    path: '/admin/teachers/:teacherId',
    handler: (context) => {
      admin(context);
      return toAdminTeacher(teacherOrFail(context.params['teacherId']));
    },
  },

  {
    method: 'PATCH',
    path: '/admin/teachers/:teacherId',
    handler: (context) => {
      admin(context);
      const teacher = teacherOrFail(context.params['teacherId']);
      const payload = body<Record<string, unknown>>(context);

      checkVersion(teacher.version, payload['version']);

      return mutate(() => {
        if (typeof payload['fullName'] === 'string') {
          teacher.fullName = payload['fullName'];
        }
        if (typeof payload['registrationNumber'] === 'string') {
          teacher.registrationNumber = payload['registrationNumber'];
        }
        if (typeof payload['institutionId'] === 'string') {
          teacher.institutionId = payload['institutionId'];
        }
        // E-mail não é editável por aqui — nem se vier no corpo.
        teacher.version++;
        return toAdminTeacher(teacher);
      });
    },
  },

  {
    method: 'POST',
    path: '/admin/teachers/:teacherId/activate',
    handler: (context) => {
      admin(context);
      const teacher = teacherOrFail(context.params['teacherId']);
      return mutate(() => {
        teacher.status = 'ACTIVE';
        teacher.version++;
        return toAdminTeacher(teacher);
      });
    },
  },

  {
    method: 'POST',
    path: '/admin/teachers/:teacherId/deactivate',
    handler: (context) => {
      admin(context);
      const teacher = teacherOrFail(context.params['teacherId']);
      return mutate(() => {
        teacher.status = 'INACTIVE';
        teacher.version++;
        // Efeito imediato: quem estiver usando agora é desconectado.
        revokeSessions(teacher.id);
        return toAdminTeacher(teacher);
      });
    },
  },

  {
    method: 'POST',
    path: '/admin/teachers/:teacherId/password-reset',
    handler: (context) => {
      admin(context);
      const teacher = teacherOrFail(context.params['teacherId']);

      return mutate(() => {
        const token = newId('reset');
        db().actionTokens.push({
          token,
          type: 'PASSWORD_RESET',
          userId: teacher.id,
          expiresAt: isoIn(3_600_000),
          usedAt: null,
        });
        console.info(
          `[mock] Redefinição para ${teacher.email}: /redefinir-senha?token=${token}`,
        );
        // O admin não recebe a senha nem o token: só o professor.
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/admin/financial-tips',
    handler: (context) => {
      admin(context);
      const search = context.query.get('search');
      const active = context.query.get('active');

      const rows = db()
        .financialTips.filter((tip) => matchesSearch(search, tip.title, tip.content))
        .filter((tip) => (active === null ? true : tip.active === (active === 'true')))
        .sort((a, b) => b.publicationDate.localeCompare(a.publicationDate))
        .map(toTip);

      return paginate(rows, context);
    },
  },

  {
    method: 'POST',
    path: '/admin/financial-tips',
    handler: (context) => {
      admin(context);
      const payload = body<{
        title: string;
        content: string;
        sourceUrl: string | null;
        publicationDate: string;
        active: boolean;
      }>(context);

      // O formato é validado como texto: nada de `Date` aqui.
      if (!/^\d{4}-\d{2}-\d{2}$/.test(payload.publicationDate ?? '')) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'publicationDate', message: 'Informe uma data no formato AAAA-MM-DD.' },
        ]);
      }

      return mutate(() => {
        const tip: DbFinancialTip = {
          id: newId('tip'),
          title: payload.title,
          content: payload.content,
          sourceUrl: payload.sourceUrl ?? null,
          publicationDate: payload.publicationDate,
          active: payload.active ?? true,
          version: 1,
        };
        db().financialTips.push(tip);
        return toTip(tip);
      });
    },
  },

  {
    method: 'GET',
    path: '/admin/financial-tips/:tipId',
    handler: (context) => {
      admin(context);
      return toTip(tipOrFail(context.params['tipId']));
    },
  },

  {
    method: 'PATCH',
    path: '/admin/financial-tips/:tipId',
    handler: (context) => {
      admin(context);
      const tip = tipOrFail(context.params['tipId']);
      const payload = body<Record<string, unknown>>(context);

      checkVersion(tip.version, payload['version']);

      if (typeof payload['publicationDate'] === 'string') {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(payload['publicationDate'])) {
          throw unprocessable('Verifique os campos destacados.', [
            { field: 'publicationDate', message: 'Informe uma data no formato AAAA-MM-DD.' },
          ]);
        }
      }

      return mutate(() => {
        if (typeof payload['title'] === 'string') {
          tip.title = payload['title'];
        }
        if (typeof payload['content'] === 'string') {
          tip.content = payload['content'];
        }
        if ('sourceUrl' in payload) {
          tip.sourceUrl = (payload['sourceUrl'] as string | null) ?? null;
        }
        if (typeof payload['publicationDate'] === 'string') {
          tip.publicationDate = payload['publicationDate'];
        }
        if (typeof payload['active'] === 'boolean') {
          tip.active = payload['active'];
        }
        tip.version++;
        return toTip(tip);
      });
    },
  },

  {
    method: 'POST',
    path: '/admin/financial-tips/:tipId/activate',
    handler: (context) => {
      admin(context);
      const tip = tipOrFail(context.params['tipId']);
      return mutate(() => {
        tip.active = true;
        tip.version++;
        return toTip(tip);
      });
    },
  },

  {
    method: 'POST',
    path: '/admin/financial-tips/:tipId/deactivate',
    handler: (context) => {
      admin(context);
      const tip = tipOrFail(context.params['tipId']);
      return mutate(() => {
        tip.active = false;
        tip.version++;
        return toTip(tip);
      });
    },
  },

  {
    method: 'DELETE',
    path: '/admin/financial-tips/:tipId',
    handler: (context) => {
      admin(context);
      const tip = tipOrFail(context.params['tipId']);

      return mutate(() => {
        // Dica já publicada (data no passado) é arquivada, não removida:
        // alunos podem tê-la visto e o histórico importa.
        const alreadyShown = tip.publicationDate <= nowIso().slice(0, 10);

        if (alreadyShown) {
          tip.active = false;
          tip.version++;
          return toTip(tip);
        }

        db().financialTips = db().financialTips.filter((t) => t.id !== tip.id);
        return NO_CONTENT;
      });
    },
  },
];
