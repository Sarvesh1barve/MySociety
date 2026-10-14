import { Injectable, signal, computed } from '@angular/core';
import { User, Membership, Context } from './models';
@Injectable({ providedIn: 'root' })
export class SessionService {
  // Access tokens live only in memory. Persistent credentials are HttpOnly cookies.
  readonly token = signal('');
  readonly expiresAt = signal(Number.POSITIVE_INFINITY);
  readonly persistent = signal(localStorage.getItem('ms.session') === 'active');
  readonly restorePending = signal(false);
  readonly authRevision = signal(0);
  readonly user = signal<User | null>(null);
  readonly memberships = signal<Membership[]>([]);
  readonly context = signal<Context | null>(null);
  readonly generation = signal(0);
  readonly online = signal(navigator.onLine);
  readonly reachable = signal(true);
  readonly status = computed(() =>
    !this.online() ? 'Offline' : this.reachable() ? 'Online' : 'Server unavailable',
  );
  constructor() {
    sessionStorage.removeItem('ms.token');
    window.addEventListener('online', () => this.online.set(true));
    window.addEventListener('offline', () => this.online.set(false));
    window.addEventListener('storage', (event) => {
      if (event.key === 'ms.session' && event.newValue !== 'active') {
        this.clear(false);
        window.location.hash = '/login';
      }
    });
  }
  can(permission?: string) {
    return !permission || !!this.context()?.permissions.includes(permission);
  }
  setToken(token: string, expiresAt?: string) {
    this.token.set(token);
    this.expiresAt.set(expiresAt ? Date.parse(expiresAt) : Number.POSITIVE_INFINITY);
  }
  clearContext() {
    this.context.set(null);
    this.generation.update((v) => v + 1);
    sessionStorage.removeItem('ms.society');
    sessionStorage.removeItem('ms.managedSociety');
    localStorage.removeItem('ms.society');
    localStorage.removeItem('ms.managedSociety');
  }
  clear(updateStorage = true) {
    this.authRevision.update((v) => v + 1);
    this.setToken('');
    this.user.set(null);
    this.memberships.set([]);
    this.clearContext();
    this.persistent.set(false);
    this.restorePending.set(false);
    if (updateStorage) localStorage.removeItem('ms.session');
  }
}
