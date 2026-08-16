import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { loadingInterceptor } from './core/interceptors/loading.interceptor';
import { refreshInterceptor } from './core/interceptors/refresh.interceptor';
import { serverClockInterceptor } from './core/interceptors/server-clock.interceptor';
import { ccPreset, ccTranslation } from './core/theme/cc-preset';
import { mockApiInterceptor } from '../mocks/mock-api.interceptor';
import { routes } from './app.routes';

registerLocaleData(localePt, 'pt-BR');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'pt-BR' },

    provideRouter(
      routes,
      // `withComponentInputBinding` liga parâmetros de rota a `input()`, o que
      // mantém `roomId` como fonte única de contexto da sala (Parte 4, §2).
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
    ),

    // A ordem dos interceptors é normativa — ver Parte 1, §5.
    // A requisição percorre a lista de cima para baixo; a resposta volta de
    // baixo para cima. Por isso `refresh` vem depois de `error`: ele precisa
    // ver o erro cru antes de o `error` convertê-lo em ApiError.
    provideHttpClient(
      withFetch(),
      withInterceptors([
        authInterceptor,
        errorInterceptor,
        refreshInterceptor,
        serverClockInterceptor,
        loadingInterceptor,
        // Último da cadeia, no lugar do backend. Inerte quando
        // `environment.useMockApi` é false.
        mockApiInterceptor,
      ]),
    ),

    providePrimeNG({
      theme: {
        preset: ccPreset,
        options: {
          darkModeSelector: '.cc-dark',
          cssLayer: {
            name: 'primeng',
            order: 'theme, base, primeng',
          },
        },
      },
      ripple: true,
      translation: ccTranslation,
    }),

    MessageService,
    ConfirmationService,
  ],
};