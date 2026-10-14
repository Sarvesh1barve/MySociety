import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { SessionService } from './session.service';
import { AppConfigService } from './app-config.service';
import { User, Membership, Context } from './models';
import { SessionTransport, SessionResponse } from './session-transport.service';
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private config = inject(AppConfigService);
  session = inject(SessionService);
  private transport = inject(SessionTransport);
  async login(login: string, password: string) {
    await this.transport.finishLogout();
    const revision = this.session.authRevision();
    const result = await this.api.send<SessionResponse>('POST', '/auth/login', {
      login,
      password,
    });
    if (revision !== this.session.authRevision()) return;
    this.session.clearContext();
    this.transport.accept(result);
    try {
      await this.loadMemberships();
    } catch (error: any) {
      if (error.status === 401) throw error;
      this.session.restorePending.set(true);
    }
  }
  async restore() {
    await this.transport.finishLogout();
    if (!this.session.persistent() && !this.session.token()) return;
    this.session.restorePending.set(true);
    if (!this.config.apiBaseUrl) return;
    const revision = this.session.authRevision();
    try {
      if (!this.session.token() || this.session.expiresAt() < Date.now() + 30000)
        await this.transport.refresh();
      const user = await this.api.get<User>('/auth/me');
      if (revision !== this.session.authRevision()) return;
      this.session.user.set(user);
      await this.loadMemberships();
      this.session.restorePending.set(false);
    } catch (error: any) {
      if (error.status === 401) this.session.clear();
    }
  }
  async loadMemberships() {
    const revision = this.session.authRevision();
    const members = await this.api.get<Membership[]>('/memberships');
    if (revision !== this.session.authRevision()) return;
    this.session.memberships.set(members);
    const available = members.filter((m) => ['ACTIVE', 'TRIAL'].includes(m.society.status));
    const managed =
      localStorage.getItem('ms.managedSociety') || sessionStorage.getItem('ms.managedSociety');
    if (managed && this.session.user()?.superAdmin) {
      try {
        await this.manageSociety(managed);
      } catch (error: any) {
        if (![403, 404].includes(error.status)) throw error;
        this.session.clearContext();
      }
      return;
    }
    const saved = localStorage.getItem('ms.society') || sessionStorage.getItem('ms.society');
    const selected =
      available.find((m) => m.societyId === saved) ||
      (available.length === 1 ? available[0] : undefined);
    if (selected) {
      try {
        await this.switchSociety(selected);
      } catch (error: any) {
        if (![403, 404].includes(error.status)) throw error;
        this.session.clearContext();
      }
    } else this.session.clearContext();
  }
  async switchSociety(membership: Membership) {
    this.session.context.set(null);
    this.session.generation.update((v) => v + 1);
    const generation = this.session.generation();
    const context = await this.api.send<Context>(
      'POST',
      '/memberships/' + membership.id + '/activate-context',
    );
    if (generation !== this.session.generation()) return;
    this.session.clearContext();
    this.session.context.set(context);
    localStorage.setItem('ms.society', context.society.id);
    sessionStorage.setItem('ms.society', context.society.id);
  }
  async manageSociety(societyId: string) {
    this.session.context.set(null);
    this.session.generation.update((v) => v + 1);
    const generation = this.session.generation();
    const context = await firstValueFrom(
      this.http.get<Context>(this.api.url('/context'), { headers: { 'X-Society-Id': societyId } }),
    );
    if (generation !== this.session.generation()) return;
    this.session.clearContext();
    this.session.context.set(context);
    localStorage.setItem('ms.managedSociety', societyId);
    sessionStorage.setItem('ms.managedSociety', societyId);
  }
  logout() {
    return this.transport.logout();
  }
}
