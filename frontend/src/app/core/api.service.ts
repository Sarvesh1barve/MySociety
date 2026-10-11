import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, catchError, throwError, timeout, tap } from 'rxjs';
import { AppConfigService } from './app-config.service';
import { SessionService } from './session.service';
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private config = inject(AppConfigService);
  url(path: string) {
    if (!this.config.apiBaseUrl) throw new Error('Configure the Society server address first.');
    return this.config.apiBaseUrl + '/api/v1' + path;
  }
  get<T = any>(path: string) {
    return firstValueFrom(this.http.get<T>(this.url(path)));
  }
  send<T = any>(method: string, path: string, body: unknown = {}, societyId?: string) {
    return firstValueFrom(
      this.http.request<T>(method, this.url(path), {
        body,
        ...(societyId ? { headers: { 'X-Society-Id': societyId } } : {}),
      }),
    );
  }
  blob(path: string) {
    return firstValueFrom(this.http.get(this.url(path), { responseType: 'blob' }));
  }
}
export const apiInterceptor: HttpInterceptorFn = (request, next) => {
  const config = inject(AppConfigService),
    session = inject(SessionService),
    router = inject(Router);
  if (!config.apiBaseUrl || !request.url.startsWith(config.apiBaseUrl + '/api/v1/'))
    return next(request);
  const publicRequest = [
    '/auth/login',
    '/auth/signup',
    '/auth/forgot-password',
    '/system/health',
    '/application/config',
  ].some((p) => request.url === config.apiBaseUrl + '/api/v1' + p);
  let headers = request.headers;
  if (!publicRequest && session.token())
    headers = headers.set('Authorization', 'Bearer ' + session.token());
  if (!publicRequest && session.context() && !headers.has('X-Society-Id'))
    headers = headers.set('X-Society-Id', session.context()!.society.id);
  if (config.ngrokSkipBrowserWarning) headers = headers.set('ngrok-skip-browser-warning', 'true');
  return next(request.clone({ headers })).pipe(
    timeout(15000),
    tap(() => session.reachable.set(true)),
    catchError((error) => {
      if (error.status === 0 || error.name === 'TimeoutError' || error.status >= 502)
        session.reachable.set(false);
      if (error.status === 401 && !publicRequest) {
        session.clear();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (!error.status || error.status >= 502) return 'Unable to connect to the Society server.';
    if (error.status === 403) return 'You do not have permission to perform this action.';
    return error.error?.message || 'The request could not be completed.';
  }
  return error instanceof Error && error.name !== 'TimeoutError'
    ? error.message
    : 'Unable to connect to the Society server.';
}
export function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
