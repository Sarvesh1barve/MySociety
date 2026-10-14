import { Component, inject, signal, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AppConfigService } from '../core/app-config.service';

@Component({
  standalone: true,
  template: ` <section class="panel settings-panel">
    <h1>Unable to connect to the Society server.</h1>
    <p>Your sign-in has been kept. We will restore your access when the server is available.</p>
    <button class="button primary" (click)="retry()" [disabled]="busy()">
      {{ busy() ? 'Connecting…' : 'Try again' }}
    </button>
    <button class="button secondary" (click)="logout()">Sign out</button>
  </section>`,
})
export class Reconnect implements OnDestroy {
  private auth = inject(AuthService);
  private config = inject(AppConfigService);
  private router = inject(Router);
  busy = signal(false);
  private timer = setInterval(() => void this.retry(), 15000);
  async retry() {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      if (!this.config.apiBaseUrl) await this.config.load();
      await this.auth.restore();
      const s = this.auth.session;
      if (!s.restorePending())
        await this.router.navigateByUrl(
          !s.user()
            ? '/login'
            : s.context()
              ? '/dashboard'
              : s.user()?.superAdmin
                ? '/platform'
                : '/select-society',
        );
    } finally {
      this.busy.set(false);
    }
  }
  async logout() {
    void this.auth.logout();
    await this.router.navigateByUrl('/login');
  }
  ngOnDestroy() {
    clearInterval(this.timer);
  }
}
