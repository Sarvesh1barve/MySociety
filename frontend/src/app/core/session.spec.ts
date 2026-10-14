import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { SessionService } from './session.service';
import { SessionTransport } from './session-transport.service';
import { AuthService } from './auth.service';
import { AppConfigService } from './app-config.service';
import { ApiService, apiInterceptor } from './api.service';
import { authGuard } from './guards';

describe('Persistent session resilience', () => {
  let http: HttpTestingController, session: SessionService, transport: SessionTransport;
  const base = 'http://localhost:8081/api/v1';
  const user = {
    id: 'owner',
    name: 'Owner',
    superAdmin: true,
    status: 'ACTIVE',
    email: 'owner@test.local',
    mobile: null,
  };
  const response = () => ({
    token: 'memory-only-token',
    expiresAt: new Date(Date.now() + 600000).toISOString(),
    user,
  });
  const pendingRequest = (path: string) => vi.waitFor(() => http.expectOne(base + path));
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    TestBed.inject(AppConfigService).apiBaseUrl = 'http://localhost:8081';
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    transport = TestBed.inject(SessionTransport);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => {
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('stores only a presence marker, never access or refresh credentials in Web Storage', () => {
    transport.accept(response());
    expect(session.token()).toBe('memory-only-token');
    expect(localStorage.getItem('ms.session')).toBe('active');
    expect(JSON.stringify(localStorage)).not.toContain('memory-only-token');
    expect(sessionStorage.getItem('ms.token')).toBeNull();
  });
  it('coalesces renewal and retries API requests only after cookie refresh', async () => {
    transport.accept(response());
    session.expiresAt.set(0);
    const api = TestBed.inject(ApiService);
    const a = api.get('/auth/me'),
      b = api.get('/memberships');
    const refresh = http.expectOne(base + '/auth/refresh');
    expect(refresh.request.withCredentials).toBe(true);
    expect(refresh.request.headers.get('X-MS-Session')).toBe('1');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush(response());
    (await pendingRequest('/auth/me')).flush(user);
    (await pendingRequest('/memberships')).flush([]);
    await Promise.all([a, b]);
  });
  it('keeps a session and society hint during an outage and restores safely later', async () => {
    transport.accept(response());
    session.setToken('');
    localStorage.setItem('ms.managedSociety', 'a');
    const auth = TestBed.inject(AuthService);
    let pending = auth.restore();
    (await pendingRequest('/auth/refresh')).error(new ProgressEvent('error'));
    await pending;
    expect(session.persistent()).toBe(true);
    expect(localStorage.getItem('ms.managedSociety')).toBe('a');
    expect(session.restorePending()).toBe(true);
    expect(String(TestBed.runInInjectionContext(() => authGuard({} as any, {} as any)))).toBe(
      '/reconnect',
    );
    pending = auth.restore();
    (await pendingRequest('/auth/refresh')).flush(response());
    (await pendingRequest('/auth/me')).flush(user);
    (await pendingRequest('/memberships')).flush([]);
    const contextRequest = await pendingRequest('/context');
    expect(contextRequest.request.headers.get('X-Society-Id')).toBe('a');
    contextRequest.flush({
      society: { id: 'a' },
      permissions: ['SOCIETY_UPDATE'],
      roles: ['SUPER_ADMIN'],
      flats: [],
    });
    await pending;
    expect(session.restorePending()).toBe(false);
    expect(session.context()?.society.id).toBe('a');
  });
  it('clears a genuinely invalid refresh session', async () => {
    transport.accept(response());
    session.setToken('');
    const pending = TestBed.inject(AuthService).restore();
    (await pendingRequest('/auth/refresh')).flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;
    expect(session.user()).toBeNull();
    expect(session.persistent()).toBe(false);
    expect(localStorage.getItem('ms.session')).toBeNull();
  });
  it('does not clear authentication on a 503, but does clear it on a real API 401', async () => {
    transport.accept(response());
    const api = TestBed.inject(ApiService);
    let pending = api.get('/auth/me').catch(() => undefined);
    http.expectOne(base + '/auth/me').flush({}, { status: 503, statusText: 'Unavailable' });
    await pending;
    expect(session.user()?.id).toBe('owner');
    pending = api.get('/auth/me').catch(() => undefined);
    http.expectOne(base + '/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    await pending;
    expect(session.user()).toBeNull();
  });
  it('offline logout clears local access, blocks restoration, and revokes when connection returns', async () => {
    transport.accept(response());
    let pending = transport.logout();
    http.expectOne(base + '/auth/logout').error(new ProgressEvent('error'));
    await pending;
    expect(session.token()).toBe('');
    expect(session.user()).toBeNull();
    expect(localStorage.getItem('ms.session')).toBe('logout');
    pending = TestBed.inject(AuthService).restore();
    (await pendingRequest('/auth/logout')).flush({});
    await pending;
    expect(localStorage.getItem('ms.session')).toBeNull();
    expect(session.persistent()).toBe(false);
  });
  it('late refresh response cannot resurrect an explicitly ended session', async () => {
    transport.accept(response());
    const refreshing = transport.refresh().catch(() => undefined);
    const refresh = http.expectOne(base + '/auth/refresh');
    const logout = transport.logout();
    refresh.flush(response());
    http.expectOne(base + '/auth/logout').flush({});
    await Promise.all([refreshing, logout]);
    expect(session.token()).toBe('');
    expect(session.user()).toBeNull();
  });
  it('late startup profile response cannot repopulate a logged-out user', async () => {
    transport.accept(response());
    const restore = TestBed.inject(AuthService).restore();
    const me = await pendingRequest('/auth/me');
    const logout = transport.logout();
    me.flush(user);
    http.expectOne(base + '/auth/logout').flush({});
    await Promise.all([restore, logout]);
    expect(session.user()).toBeNull();
    expect(session.memberships()).toEqual([]);
  });
});
