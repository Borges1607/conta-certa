import type { AuthTokens, LoginResponse } from '../../app/core/models/auth';
import { isPasswordValid } from '../../app/core/models/auth';
import type { InstitutionOption } from '../../app/core/models/institution';
import type { UserSummary } from '../../app/core/models/user';
import { db, type DbUser } from '../db';
import {
  conflict,
  forbidden,
  gone,
  notFound,
  problem,
  unauthorized,
  unprocessable,
  NO_CONTENT,
  type MockContext,
  type MockRoute,
} from '../router';
import { isPast, isoIn, mutate, newId, nowIso, requireUser, toUserSummary } from '../support';

/**
 * Handlers de autenticação e conta — §4 e §5.2 da spec de integração.
 *
 * Aplicam as regras de verdade: token expira, refresh rotaciona, conta pendente
 * não entra. É isso que permite exercitar o ciclo de refresh e os estados de
 * bloqueio em desenvolvimento.
 */

const ACCESS_TTL_SECONDS = 900; // 15 minutos
const REFRESH_TTL_SECONDS = 604_800; // 7 dias

function issueTokens(user: DbUser): AuthTokens {
  const tokens: AuthTokens = {
    accessToken: newId('at'),
    refreshToken: newId('rt'),
    tokenType: 'Bearer',
    accessExpiresIn: ACCESS_TTL_SECONDS,
    refreshExpiresIn: REFRESH_TTL_SECONDS,
  };

  db().sessions.push({
    refreshToken: tokens.refreshToken,
    accessToken: tokens.accessToken,
    userId: user.id,
    accessExpiresAt: isoIn(ACCESS_TTL_SECONDS * 1000),
    refreshExpiresAt: isoIn(REFRESH_TTL_SECONDS * 1000),
    revoked: false,
  });

  return tokens;
}

function body<T>(context: MockContext): T {
  return (context.body ?? {}) as T;
}

function findUserByEmail(email: string): DbUser | undefined {
  const needle = email.trim().toLowerCase();
  return db().users.find((u) => u.email.toLowerCase() === needle);
}

export const authRoutes: MockRoute[] = [
  {
    method: 'POST',
    path: '/auth/login',
    handler: (context) => {
      const { email, password } = body<{ email: string; password: string }>(context);
      const user = findUserByEmail(email ?? '');

      // Credencial errada e usuário inexistente respondem igual: revelar a
      // diferença entregaria quais e-mails existem.
      if (!user || user.password !== password) {
        throw unauthorized('E-mail ou senha inválidos.');
      }
      if (user.status === 'INACTIVE') {
        throw problem(403, 'ACCOUNT_INACTIVE', 'Sua conta está desativada.');
      }
      if (!user.emailVerified || user.status === 'PENDING') {
        throw problem(403, 'EMAIL_NOT_VERIFIED', 'Confirme seu e-mail antes de entrar.');
      }

      return mutate<LoginResponse>(() => ({
        ...issueTokens(user),
        user: toUserSummary(user),
      }));
    },
  },

  {
    method: 'POST',
    path: '/auth/refresh',
    handler: (context) => {
      const { refreshToken } = body<{ refreshToken: string }>(context);
      const session = db().sessions.find((s) => s.refreshToken === refreshToken && !s.revoked);

      if (!session || isPast(session.refreshExpiresAt)) {
        throw unauthorized('Refresh token inválido ou expirado.');
      }

      const user = db().users.find((u) => u.id === session.userId);
      if (!user || user.status === 'INACTIVE') {
        throw unauthorized('Sessão encerrada.');
      }

      return mutate<AuthTokens>(() => {
        // Rotação: o refresh token antigo morre no uso (§2.2 da spec).
        session.revoked = true;
        return issueTokens(user);
      });
    },
  },

  {
    method: 'POST',
    path: '/auth/logout',
    handler: (context) => {
      const token = context.accessToken;
      return mutate(() => {
        // Encerra **apenas** a sessão atual; outros dispositivos continuam.
        const session = db().sessions.find((s) => s.accessToken === token);
        if (session) {
          session.revoked = true;
        }
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/auth/student-registration',
    handler: (context) => {
      const payload = body<{
        fullName: string;
        email: string;
        password: string;
        registrationNumber: string;
        institutionId: string;
      }>(context);

      const fieldErrors: { field: string; message: string }[] = [];

      if (findUserByEmail(payload.email ?? '')) {
        fieldErrors.push({ field: 'email', message: 'Este e-mail já está cadastrado.' });
      }
      if (
        db().users.some(
          (u) =>
            u.registrationNumber === payload.registrationNumber &&
            u.institutionId === payload.institutionId,
        )
      ) {
        fieldErrors.push({
          field: 'registrationNumber',
          message: 'Esta matrícula já está em uso nesta instituição.',
        });
      }
      if (!isPasswordValid(payload.password ?? '')) {
        fieldErrors.push({
          field: 'password',
          message: 'A senha precisa ter de 8 a 72 caracteres, com ao menos uma letra e um número.',
        });
      }
      const institution = db().institutions.find((i) => i.id === payload.institutionId);
      if (!institution?.active) {
        fieldErrors.push({ field: 'institutionId', message: 'Instituição inválida ou inativa.' });
      }

      if (fieldErrors.length > 0) {
        throw unprocessable('Verifique os campos destacados.', fieldErrors);
      }

      return mutate(() => {
        const user: DbUser = {
          id: newId('user'),
          role: 'STUDENT',
          status: 'PENDING',
          fullName: payload.fullName,
          email: payload.email,
          password: payload.password,
          registrationNumber: payload.registrationNumber,
          institutionId: payload.institutionId,
          emailVerified: false,
          mustChangePassword: false,
          version: 1,
          createdAt: nowIso(),
        };
        db().users.push(user);

        // Em produção isto viraria e-mail. Aqui o token vai para o console,
        // para dar para completar o fluxo em desenvolvimento.
        const token = newId('verify');
        db().actionTokens.push({
          token,
          type: 'EMAIL_VERIFICATION',
          userId: user.id,
          expiresAt: isoIn(86_400_000),
          usedAt: null,
        });
        console.info(
          `[mock] Confirmação de e-mail para ${user.email}: /verificar-email?token=${token}`,
        );

        // 202 Accepted: o cadastro foi aceito, mas ainda não autentica.
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/auth/verify-email',
    handler: (context) => {
      const { token } = body<{ token: string }>(context);
      const action = db().actionTokens.find(
        (t) => t.token === token && t.type === 'EMAIL_VERIFICATION',
      );

      if (!action) {
        throw notFound('Link de confirmação inválido.');
      }
      if (action.usedAt) {
        throw conflict('TOKEN_ALREADY_USED', 'Este link já foi utilizado.');
      }
      if (isPast(action.expiresAt)) {
        throw gone('Este link de confirmação expirou.');
      }

      return mutate(() => {
        action.usedAt = nowIso();
        const user = db().users.find((u) => u.id === action.userId);
        if (user) {
          user.emailVerified = true;
          user.status = 'ACTIVE';
        }
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/auth/resend-verification',
    handler: (context) => {
      const { email } = body<{ email: string }>(context);
      const user = findUserByEmail(email ?? '');

      return mutate(() => {
        if (user && !user.emailVerified) {
          const token = newId('verify');
          db().actionTokens.push({
            token,
            type: 'EMAIL_VERIFICATION',
            userId: user.id,
            expiresAt: isoIn(86_400_000),
            usedAt: null,
          });
          console.info(`[mock] Reenvio para ${user.email}: /verificar-email?token=${token}`);
        }
        // Resposta idêntica em qualquer caso: não revelamos quem existe.
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/auth/forgot-password',
    handler: (context) => {
      const { email } = body<{ email: string }>(context);
      const user = findUserByEmail(email ?? '');

      return mutate(() => {
        if (user) {
          const token = newId('reset');
          db().actionTokens.push({
            token,
            type: 'PASSWORD_RESET',
            userId: user.id,
            expiresAt: isoIn(3_600_000),
            usedAt: null,
          });
          console.info(`[mock] Recuperação para ${user.email}: /redefinir-senha?token=${token}`);
        }
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/auth/reset-password',
    handler: (context) => {
      const { token, newPassword } = body<{ token: string; newPassword: string }>(context);
      const action = db().actionTokens.find((t) => t.token === token && t.type === 'PASSWORD_RESET');

      if (!action) {
        throw notFound('Link de recuperação inválido.');
      }
      if (action.usedAt || isPast(action.expiresAt)) {
        throw gone('Este link de recuperação não vale mais.');
      }
      if (!isPasswordValid(newPassword ?? '')) {
        throw unprocessable('Verifique os campos destacados.', [
          {
            field: 'newPassword',
            message: 'A senha precisa ter de 8 a 72 caracteres, com ao menos uma letra e um número.',
          },
        ]);
      }

      return mutate(() => {
        action.usedAt = nowIso();
        const user = db().users.find((u) => u.id === action.userId);
        if (user) {
          user.password = newPassword;
          user.mustChangePassword = false;
          // Redefinir senha derruba as sessões abertas.
          for (const session of db().sessions.filter((s) => s.userId === user.id)) {
            session.revoked = true;
          }
        }
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'POST',
    path: '/auth/accept-teacher-invite',
    handler: (context) => {
      const { token, password } = body<{ token: string; password: string }>(context);
      const action = db().actionTokens.find((t) => t.token === token && t.type === 'TEACHER_INVITE');

      if (!action) {
        throw notFound('Convite inválido.');
      }
      if (action.usedAt || isPast(action.expiresAt)) {
        throw gone('Este convite expirou.');
      }
      if (!isPasswordValid(password ?? '')) {
        throw unprocessable('Verifique os campos destacados.', [
          {
            field: 'password',
            message: 'A senha precisa ter de 8 a 72 caracteres, com ao menos uma letra e um número.',
          },
        ]);
      }

      return mutate(() => {
        action.usedAt = nowIso();
        const user = db().users.find((u) => u.id === action.userId);
        if (user) {
          user.password = password;
          user.status = 'ACTIVE';
          user.emailVerified = true;
          user.mustChangePassword = false;
        }
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/me',
    handler: (context): UserSummary => toUserSummary(requireUser(context)),
  },

  {
    method: 'PATCH',
    path: '/me',
    handler: (context): UserSummary => {
      const user = requireUser(context);
      const { fullName } = body<{ fullName: string }>(context);

      if (!fullName || fullName.trim().length < 3) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'fullName', message: 'Informe o nome completo.' },
        ]);
      }

      return mutate(() => {
        user.fullName = fullName.trim();
        user.version++;
        return toUserSummary(user);
      });
    },
  },

  {
    method: 'POST',
    path: '/me/change-password',
    handler: (context) => {
      const user = requireUser(context);
      const { currentPassword, newPassword } = body<{
        currentPassword: string;
        newPassword: string;
      }>(context);

      if (user.password !== currentPassword) {
        throw unprocessable('Verifique os campos destacados.', [
          { field: 'currentPassword', message: 'Senha atual incorreta.' },
        ]);
      }
      if (!isPasswordValid(newPassword ?? '')) {
        throw unprocessable('Verifique os campos destacados.', [
          {
            field: 'newPassword',
            message: 'A senha precisa ter de 8 a 72 caracteres, com ao menos uma letra e um número.',
          },
        ]);
      }

      return mutate(() => {
        user.password = newPassword;
        user.mustChangePassword = false;
        return NO_CONTENT;
      });
    },
  },

  {
    method: 'GET',
    path: '/institutions/options',
    handler: (context): InstitutionOption[] => {
      const activeOnly = context.query.get('active') !== 'false';
      return db()
        .institutions.filter((i) => (activeOnly ? i.active : true))
        .map((i) => ({ id: i.id, name: i.name, cnpj: i.cnpj }));
    },
  },
];

/** Reexportado para o painel de desenvolvimento poder simular `403`. */
export { forbidden };
