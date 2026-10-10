import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { SessionService } from './session.service';
import { AppConfigService } from './app-config.service';
import { User, Membership, Context } from './models';
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private config = inject(AppConfigService);
  session = inject(SessionService);
  async login(login: string, password: string) {
    const result = await this.api.send<{ token: string; user: User }>('POST', '/auth/login', {
      login,
      password,
    });
    this.session.setToken(result.token);
    this.session.user.set(result.user);
    await this.loadMemberships();
  }
  async restore() {
    if (!this.session.token() || !this.config.apiBaseUrl) return;
    try {
      this.session.user.set(await this.api.get<User>('/auth/me'));
      await this.loadMemberships();
    } catch (error: any) {
      if (error.status === 401) this.session.clear();
    }
  }
  async loadMemberships() {
    const members = await this.api.get<Membership[]>('/memberships');
    this.session.memberships.set(members);
    const saved = sessionStorage.getItem('ms.society');
    const selected =
      members.find((m) => m.societyId === saved) || (members.length === 1 ? members[0] : undefined);
    if (selected) await this.switchSociety(selected);
  }
  async switchSociety(membership: Membership) {
    this.session.clearContext();
    const context = await this.api.send<Context>(
      'POST',
      '/memberships/' + membership.id + '/activate-context',
    );
    this.session.context.set(context);
    sessionStorage.setItem('ms.society', context.society.id);
  }
  async manageSociety(societyId: string) {
    this.session.clearContext();
    const context = await firstValueFrom(
      this.http.get<Context>(this.api.url('/context'), { headers: { 'X-Society-Id': societyId } }),
    );
    this.session.context.set(context);
  }
  logout() {
    this.session.clear();
  }
}
