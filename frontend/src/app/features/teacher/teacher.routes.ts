import { Routes } from '@angular/router';

import { authGuard, passwordChangeGuard, roleGuard } from '../../core/auth/guards';

/**
 * Rotas do professor — Parte 5, §2.
 *
 * Os guards ficam no shell, não em cada folha: assim é impossível adicionar uma
 * tela nova e esquecer de protegê-la.
 *
 * O detalhe da sala é uma rota só, com abas no fragmento da URL — recarregar ou
 * compartilhar o link preserva a aba aberta (Parte 5, §2).
 */
export const teacherRoutes: Routes = [
  {
    path: '',
    canActivate: [authGuard, roleGuard('TEACHER'), passwordChangeGuard],
    loadComponent: () =>
      import('./layout/teacher-shell/teacher-shell').then((m) => m.TeacherShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Painel · Conta Certa',
        loadComponent: () =>
          import('./pages/dashboard/dashboard').then((m) => m.TeacherDashboardPage),
      },
      {
        path: 'salas',
        pathMatch: 'full',
        title: 'Salas · Conta Certa',
        loadComponent: () => import('./pages/rooms/rooms').then((m) => m.TeacherRoomsPage),
      },
      {
        path: 'salas/:roomId',
        title: 'Sala · Conta Certa',
        loadComponent: () =>
          import('./pages/room-detail/room-detail').then((m) => m.RoomDetailPage),
      },
      {
        path: 'licoes',
        pathMatch: 'full',
        title: 'Lições · Conta Certa',
        loadComponent: () => import('./pages/lessons/lessons').then((m) => m.LessonsPage),
      },
      {
        path: 'licoes/:lessonId',
        pathMatch: 'full',
        title: 'Editar lição · Conta Certa',
        loadComponent: () =>
          import('./pages/lesson-editor/lesson-editor').then((m) => m.LessonEditorPage),
      },
      {
        path: 'licoes/:lessonId/questoes',
        title: 'Questões · Conta Certa',
        loadComponent: () =>
          import('./pages/lesson-questions/lesson-questions').then((m) => m.LessonQuestionsPage),
      },
      {
        path: 'videos',
        title: 'Videoaulas · Conta Certa',
        loadComponent: () => import('./pages/videos/videos').then((m) => m.TeacherVideosPage),
      },
      {
        path: 'materiais',
        title: 'Materiais · Conta Certa',
        loadComponent: () =>
          import('./pages/materials/materials').then((m) => m.TeacherMaterialsPage),
      },
      {
        path: 'relatorios',
        title: 'Relatórios · Conta Certa',
        loadComponent: () => import('./pages/reports/reports').then((m) => m.ReportsPage),
      },
      { path: '**', redirectTo: '' },
    ],
  },
];
