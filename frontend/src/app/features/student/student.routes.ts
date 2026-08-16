import { CanDeactivateFn, Routes } from '@angular/router';

import { authGuard, passwordChangeGuard, roleGuard } from '../../core/auth/guards';
import { RoomContextStore } from './data/room-context.store';

/** Confirma o abandono de uma tentativa em andamento — Parte 4, §6.5. */
const attemptLeaveGuard: CanDeactivateFn<{ canDeactivate: () => Promise<boolean> }> = (component) =>
  component.canDeactivate();

/**
 * Rotas do aluno — Parte 4, §2.
 *
 * Duas decisões estruturais:
 *
 * 1. `RoomContextStore` é fornecido **aqui**, e não em `root`. Ao sair da área
 *    do aluno, o injetor da rota morre e nada da sala fica em memória.
 * 2. As telas de tentativa ficam **fora** do shell e fora do prefixo de sala:
 *    a API as endereça por `attemptId`, e a tela precisa da atenção inteira do
 *    aluno, sem menu para clicar por engano (Parte 4, §6.5).
 */
export const studentRoutes: Routes = [
  {
    path: '',
    canActivate: [authGuard, roleGuard('STUDENT'), passwordChangeGuard],
    providers: [RoomContextStore],
    children: [
      {
        path: 'tentativas/:attemptId',
        title: 'Tentativa · Conta Certa',
        canDeactivate: [attemptLeaveGuard],
        loadComponent: () => import('./pages/attempt/attempt').then((m) => m.AttemptPage),
      },
      {
        path: 'tentativas/:attemptId/resultado',
        title: 'Resultado · Conta Certa',
        loadComponent: () =>
          import('./pages/attempt-result/attempt-result').then((m) => m.AttemptResultPage),
      },
      {
        path: '',
        loadComponent: () =>
          import('./layout/student-shell/student-shell').then((m) => m.StudentShellComponent),
        children: [
          {
            path: 'salas',
            pathMatch: 'full',
            title: 'Minhas salas · Conta Certa',
            loadComponent: () => import('./pages/rooms/rooms').then((m) => m.StudentRoomsPage),
          },
          {
            path: 'salas/:roomId',
            pathMatch: 'full',
            title: 'Minha sala · Conta Certa',
            loadComponent: () =>
              import('./pages/room-dashboard/room-dashboard').then((m) => m.RoomDashboardPage),
          },
          {
            path: 'salas/:roomId/trilha',
            title: 'Trilha · Conta Certa',
            loadComponent: () =>
              import('./pages/lesson-track/lesson-track').then((m) => m.LessonTrackPage),
          },
          {
            path: 'salas/:roomId/licoes/:lessonId',
            title: 'Lição · Conta Certa',
            loadComponent: () =>
              import('./pages/lesson-detail/lesson-detail').then((m) => m.LessonDetailPage),
          },
          {
            path: 'salas/:roomId/videos',
            title: 'Videoaulas · Conta Certa',
            loadComponent: () => import('./pages/videos/videos').then((m) => m.StudentVideosPage),
          },
          {
            path: 'salas/:roomId/materiais',
            title: 'Materiais · Conta Certa',
            loadComponent: () =>
              import('./pages/materials/materials').then((m) => m.StudentMaterialsPage),
          },
          {
            path: 'salas/:roomId/ranking',
            title: 'Ranking · Conta Certa',
            loadComponent: () => import('./pages/ranking/ranking').then((m) => m.RankingPage),
          },
          {
            path: 'salas/:roomId/conquistas',
            title: 'Conquistas · Conta Certa',
            loadComponent: () =>
              import('./pages/achievements/achievements').then((m) => m.AchievementsPage),
          },
          { path: '', pathMatch: 'full', redirectTo: 'salas' },
        ],
      },
    ],
  },
];
