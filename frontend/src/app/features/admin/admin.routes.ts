import { Routes } from '@angular/router';

import { authGuard, passwordChangeGuard, roleGuard } from '../../core/auth/guards';

/**
 * Rotas do administrador — Parte 6, §2.
 *
 * Os guards ficam no shell, não em cada folha: assim é impossível adicionar uma
 * tela nova e esquecer de protegê-la. Eles são conveniência de navegação; a
 * autorização real continua sendo do servidor (Parte 1, §6).
 */
export const adminRoutes: Routes = [
  {
    path: '',
    canActivate: [authGuard, roleGuard('ADMIN'), passwordChangeGuard],
    loadComponent: () =>
      import('./layout/admin-shell/admin-shell').then((m) => m.AdminShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Painel — Conta Certa',
        loadComponent: () =>
          import('./pages/dashboard/dashboard-page').then((m) => m.AdminDashboardPage),
      },
      {
        path: 'instituicoes',
        pathMatch: 'full',
        title: 'Instituições — Conta Certa',
        loadComponent: () =>
          import('./pages/institutions/institutions-page').then((m) => m.InstitutionsPage),
      },
      {
        path: 'instituicoes/:institutionId',
        title: 'Instituição — Conta Certa',
        loadComponent: () =>
          import('./pages/institution-detail/institution-detail-page').then(
            (m) => m.InstitutionDetailPage,
          ),
      },
      {
        path: 'professores',
        pathMatch: 'full',
        title: 'Professores — Conta Certa',
        loadComponent: () => import('./pages/teachers/teachers-page').then((m) => m.TeachersPage),
      },
      {
        path: 'professores/:teacherId',
        title: 'Professor — Conta Certa',
        loadComponent: () =>
          import('./pages/teacher-detail/teacher-detail-page').then((m) => m.TeacherDetailPage),
      },
      {
        path: 'dicas',
        title: 'Dicas financeiras — Conta Certa',
        loadComponent: () =>
          import('./pages/financial-tips/financial-tips-page').then((m) => m.FinancialTipsPage),
      },
      { path: '**', redirectTo: '' },
    ],
  },
];
