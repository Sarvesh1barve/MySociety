import { Injectable, signal, computed } from '@angular/core';
import { User, Membership, Context } from './models';
@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly token = signal(sessionStorage.getItem('ms.token') || '');
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
    window.addEventListener('online', () => this.online.set(true));
    window.addEventListener('offline', () => this.online.set(false));
  }
  can(permission?: string) {
    return !permission || !!this.context()?.permissions.includes(permission);
  }
  setToken(token: string) {
    this.token.set(token);
    if (token) sessionStorage.setItem('ms.token', token);
    else sessionStorage.removeItem('ms.token');
  }
  clearContext() {
    this.context.set(null);
    this.generation.update((v) => v + 1);
    sessionStorage.removeItem('ms.society');
    sessionStorage.removeItem('ms.managedSociety');
  }
  clear() {
    this.setToken('');
    this.user.set(null);
    this.memberships.set([]);
    this.clearContext();
  }
}
