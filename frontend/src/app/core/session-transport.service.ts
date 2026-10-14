import { Injectable, inject } from '@angular/core';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { AppConfigService } from './app-config.service';
import { SessionService } from './session.service';
import { User } from './models';

export interface SessionResponse {
  token: string;
  expiresAt: string;
  user: User;
}

@Injectable({ providedIn: 'root' })
export class SessionTransport {
  private http = new HttpClient(inject(HttpBackend));
  private config = inject(AppConfigService);
  private session = inject(SessionService);
  private refreshing?: Promise<void>;
  private loggingOut?: Promise<void>;
  private epoch = 0;

  accept(result: SessionResponse) {
    this.session.setToken(result.token, result.expiresAt);
    this.session.user.set(result.user);
    this.session.persistent.set(true);
    localStorage.setItem('ms.session', 'active');
  }
  private post<T>(path: string) {
    const headers: Record<string, string> = { 'X-MS-Session': '1' };
    if (this.config.ngrokSkipBrowserWarning) headers['ngrok-skip-browser-warning'] = 'true';
    return firstValueFrom(
      this.http
        .post<T>(
          this.config.apiBaseUrl + '/api/v1/auth/' + path,
          {},
          { withCredentials: true, headers },
        )
        .pipe(timeout(15000)),
    );
  }
  refresh(): Promise<void> {
    if (this.refreshing) return this.refreshing;
    const epoch = this.epoch;
    this.refreshing = this.post<SessionResponse>('refresh')
      .then((result) => {
        if (epoch !== this.epoch || localStorage.getItem('ms.session') !== 'active')
          throw new Error('Session ended.');
        this.accept(result);
        this.session.reachable.set(true);
      })
      .catch((error) => {
        if (epoch === this.epoch) {
          if (error.status === 401) this.session.clear();
          else this.session.reachable.set(false);
        }
        throw error;
      })
      .finally(() => {
        this.refreshing = undefined;
      });
    return this.refreshing;
  }
  logout(): Promise<void> {
    if (this.loggingOut) return this.loggingOut;
    this.epoch++;
    this.session.clear();
    // No cookie access from JS: remember an offline logout until the server can invalidate it.
    localStorage.setItem('ms.session', 'logout');
    this.loggingOut = this.post('logout')
      .then(() => {
        if (localStorage.getItem('ms.session') === 'logout') localStorage.removeItem('ms.session');
      })
      .catch(() => {
        /* Never restore a session while logout is pending. */
      })
      .finally(() => {
        this.loggingOut = undefined;
      });
    return this.loggingOut;
  }
  async finishLogout() {
    if (localStorage.getItem('ms.session') === 'logout') await this.logout();
  }
}
