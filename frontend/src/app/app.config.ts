import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideAppInitializer,
  inject,
  isDevMode,
} from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { AppConfigService } from './core/app-config.service';
import { ApiService, apiInterceptor } from './core/api.service';
import { AuthService } from './core/auth.service';
import { I18nService } from './core/i18n.service';
import { SessionService } from './core/session.service';
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([apiInterceptor])),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
    provideAppInitializer(async () => {
      const config = inject(AppConfigService),
        api = inject(ApiService),
        auth = inject(AuthService),
        i18n = inject(I18nService),
        session = inject(SessionService);
      await Promise.all([config.load(), i18n.load()]);
      if (config.error()) session.reachable.set(false);
      if (config.apiBaseUrl) {
        try {
          config.setBrand(await api.get('/application/config'));
        } catch {}
        await auth.restore();
      }
    }),
  ],
};
