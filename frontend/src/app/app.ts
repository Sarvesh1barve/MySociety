import { Component, inject, signal, effect, OnDestroy } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Icon } from './shared/icon';
import { TranslatePipe, I18nService } from './core/i18n.service';
import { SessionService } from './core/session.service';
import { AppConfigService } from './core/app-config.service';
import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, Icon, TranslatePipe],
  templateUrl: './app.html',
})
export class App implements OnDestroy {
  session = inject(SessionService);
  config = inject(AppConfigService);
  auth = inject(AuthService);
  api = inject(ApiService);
  router = inject(Router);
  i18n = inject(I18nService);
  menuOpen = signal(false);
  unread = signal(0);
  loginPage = signal(this.router.url.startsWith('/login') || this.router.url === '/');
  private nav = this.router.events.subscribe((e) => {
    if (e instanceof NavigationEnd) {
      this.loginPage.set(e.urlAfterRedirects.startsWith('/login'));
      this.menuOpen.set(false);
    }
  });
  private timer = setInterval(() => void this.poll(), 45000);
  private contextEffect = effect(() => {
    this.session.generation();
    this.session.context();
    this.unread.set(0);
    void this.poll();
  });
  communityNav = [
    { label: 'Dashboard', path: '/dashboard', icon: 'home' },
    { label: 'Residents', path: '/residents', icon: 'people', permission: 'RESIDENT_READ' },
    { label: 'Flats & buildings', path: '/flats', icon: 'building', permission: 'FLAT_READ' },
    { label: 'Visitors', path: '/visitors', icon: 'visitor', permission: 'VISITOR_READ' },
    { label: 'Parcels', path: '/parcels', icon: 'parcel', permission: 'PARCEL_READ' },
    { label: 'Complaints', path: '/complaints', icon: 'complaint', permission: 'COMPLAINT_READ' },
    { label: 'Notice board', path: '/notices', icon: 'notice', permission: 'NOTICE_READ' },
    {
      label: 'Meetings & minutes',
      path: '/meetings',
      icon: 'calendar',
      permission: 'MEETING_READ',
    },
    { label: 'Actions', path: '/tasks', icon: 'task', permission: 'TASK_READ' },
    { label: 'Documents', path: '/documents', icon: 'file', permission: 'DOCUMENT_READ' },
    { label: 'Reports', path: '/reports', icon: 'chart', permission: 'REPORT_VIEW' },
    {
      label: 'Emergency contacts',
      path: '/emergency-contacts',
      icon: 'phone',
      permission: 'EMERGENCY_READ',
    },
  ];
  platformNav = [
    { label: 'Platform overview', path: '/platform', icon: 'home' },
    { label: 'Societies', path: '/societies', icon: 'building' },
    { label: 'Platform users', path: '/platform-users', icon: 'people' },
    { label: 'Application branding', path: '/branding', icon: 'settings' },
    { label: 'Roles & permissions', path: '/roles', icon: 'shield' },
    { label: 'System audit', path: '/system-audit', icon: 'file' },
    { label: 'Diagnostics', path: '/diagnostics', icon: 'chart' },
  ];
  async poll() {
    if (!this.session.token() || !this.session.context() || document.visibilityState !== 'visible')
      return;
    const generation = this.session.generation();
    try {
      const n = await this.api.get<any[]>('/notifications');
      if (generation === this.session.generation())
        this.unread.set(n.filter((v) => !v.readAt).length);
    } catch {}
  }
  initials() {
    return (
      this.session
        .user()
        ?.name.split(' ')
        .slice(0, 2)
        .map((s) => s[0])
        .join('') || 'MS'
    );
  }
  logout() {
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }
  ngOnDestroy() {
    clearInterval(this.timer);
    this.nav.unsubscribe();
    this.contextEffect.destroy();
  }
}
