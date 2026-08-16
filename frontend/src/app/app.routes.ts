import { Routes } from '@angular/router';

import { accountRoutes } from './features/account/account.routes';
import { publicRoutes } from './features/public/public.routes';

/**
 * Rotas raiz. O mapa completo está na visão geral das specs, §5.
 *
 * Cada área entra como `loadChildren`/`loadComponent`, para o pacote inicial
 * carregar apenas o núcleo, o shell público e o login (Parte 7, §5).
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },

  ...publicRoutes,
  ...accountRoutes,

  {
    path: 'aluno',
    loadChildren: () => import('./features/student/student.routes').then((m) => m.studentRoutes),
  },

  {
    path: 'professor',
    loadChildren: () => import('./features/teacher/teacher.routes').then((m) => m.teacherRoutes),
  },

  {
    path: 'admin',
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
  },

  {
    path: '403',
    title: 'Sem permissão · Conta Certa',
    data: { kind: 'forbidden' },
    loadComponent: () => import('./features/errors/error-page').then((m) => m.ErrorPage),
  },
  {
    path: '404',
    title: 'Não encontrado · Conta Certa',
    data: { kind: 'not-found' },
    loadComponent: () => import('./features/errors/error-page').then((m) => m.ErrorPage),
  },
  { path: '**', redirectTo: '404' },
];
