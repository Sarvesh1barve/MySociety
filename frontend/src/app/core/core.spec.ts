import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AppConfigService } from './app-config.service';
import { ApiService, apiInterceptor } from './api.service';
import { AuthService } from './auth.service';
import { SessionService } from './session.service';
import { permissionGuard, societyGuard, platformGuard } from './guards';
import { Context, Membership } from './models';
import { ThemeService } from './theme.service';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
const context: Context = {
  society: {
    id: 'society-a',
    name: 'Society A',
    code: 'A',
    status: 'ACTIVE',
    timezone: 'Asia/Kolkata',
  },
  permissions: ['VISITOR_READ'],
  roles: ['RESIDENT_OWNER'],
  flats: ['flat-a'],
};
describe('AppConfigService', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('loads one central API origin and enables ngrok header only on a ngrok host', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          apiBaseUrl: 'https://demo.ngrok-free.app/',
          ngrokSkipBrowserWarning: true,
        }),
      }),
    );
    const c = new AppConfigService();
    await c.load();
    expect(c.apiBaseUrl).toBe('https://demo.ngrok-free.app');
    expect(c.ngrokSkipBrowserWarning).toBe(true);
    expect(c.error()).toBe('');
  });
  it('does not send the ngrok bypass header to other hosts', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          apiBaseUrl: 'https://api.example.org',
          ngrokSkipBrowserWarning: true,
        }),
      }),
    );
    const c = new AppConfigService();
    await c.load();
    expect(c.ngrokSkipBrowserWarning).toBe(false);
  });
  it('fails safely for an invalid runtime configuration', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue({ ok: true, json: async () => ({ apiBaseUrl: 'javascript:alert(1)' }) }),
    );
    const c = new AppConfigService();
    await c.load();
    expect(c.apiBaseUrl).toBe('');
    expect(c.error()).toBe('Invalid API address.');
  });
  it('updates the platform title without changing society identity', () => {
    const c = new AppConfigService();
    c.setBrand({ applicationName: 'SocietyConnect', shortName: 'Connect' });
    expect(document.title).toBe('SocietyConnect');
    expect(context.society.name).toBe('Society A');
  });
});
describe('Authentication, permissions and society context', () => {
  let http: HttpTestingController;
  let session: SessionService;
  let api: ApiService;
  let auth: AuthService;
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
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
    api = TestBed.inject(ApiService);
    auth = TestBed.inject(AuthService);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
  });
  it('applies authentication and active society to API calls centrally', async () => {
    session.setToken('unit-test-token');
    session.context.set(context);
    const pending = api.get('/visitors');
    const req = http.expectOne('http://localhost:8081/api/v1/visitors');
    expect(req.request.headers.get('Authorization')).toBe('Bearer unit-test-token');
    expect(req.request.headers.get('X-Society-Id')).toBe('society-a');
    req.flush([]);
    await pending;
  });
  it('omits private session headers on the public health endpoint', async () => {
    session.setToken('unit-test-token');
    session.context.set(context);
    const pending = api.get('/system/health');
    const req = http.expectOne('http://localhost:8081/api/v1/system/health');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.has('X-Society-Id')).toBe(false);
    req.flush({ status: 'UP' });
    await pending;
  });
  it('keeps recovery public without leaking an existing session or society header', async () => {
    session.setToken('unit-test-token');
    session.context.set(context);
    const pending = api.send('POST', '/auth/forgot-password', {});
    const req = http.expectOne('http://localhost:8081/api/v1/auth/forgot-password');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.has('X-Society-Id')).toBe(false);
    req.flush({ message: 'Done' });
    await pending;
  });
  it('applies only the selected society palette and clears it on logout', () => {
    TestBed.inject(ThemeService);
    session.context.set({ ...context, society: { ...context.society, theme: 'OCEAN' } });
    TestBed.tick();
    expect(document.documentElement.dataset['societyTheme']).toBe('OCEAN');
    session.context.set({ ...context, society: { ...context.society, theme: 'untrusted-css' } });
    TestBed.tick();
    expect(document.documentElement.dataset['societyTheme']).toBe('FOREST');
    session.context.set({ ...context, society: { ...context.society, theme: 'INDIGO' } });
    TestBed.tick();
    session.clear();
    TestBed.tick();
    expect(document.documentElement.dataset['societyTheme']).toBe('FOREST');
  });
  it('stores authentication only after successful login', async () => {
    const pending = auth.login('resident@example.org', 'not-a-real-password');
    await Promise.resolve();
    http.expectOne('http://localhost:8081/api/v1/auth/login').flush({
      token: 'test-access-token',
      user: { id: 'u', name: 'Resident', superAdmin: false },
    });
    await Promise.resolve();
    http.expectOne('http://localhost:8081/api/v1/memberships').flush([]);
    await pending;
    expect(session.token()).toBe('test-access-token');
    expect(session.user()?.id).toBe('u');
  });
  it('public signup never attaches an existing token or society', async () => {
    session.setToken('old-token');
    session.context.set(context);
    const pending = api.send('POST', '/auth/signup', {});
    const r = http.expectOne('http://localhost:8081/api/v1/auth/signup');
    expect(r.request.headers.has('Authorization')).toBe(false);
    expect(r.request.headers.has('X-Society-Id')).toBe(false);
    r.flush({});
    await pending;
  });
  it('explicit membership target does not overwrite the active context', async () => {
    session.context.set(context);
    const pending = api.send('POST', '/society-memberships', {}, 'society-b');
    const r = http.expectOne('http://localhost:8081/api/v1/society-memberships');
    expect(r.request.headers.get('X-Society-Id')).toBe('society-b');
    r.flush({});
    await pending;
    expect(session.context()?.society.id).toBe('society-a');
  });
  it('one approved society opens automatically, but a disabled society does not', async () => {
    let pending = auth.loadMemberships();
    http.expectOne('http://localhost:8081/api/v1/memberships').flush([
      {
        id: 'member-a',
        societyId: 'society-a',
        roles: 'RESIDENT_OWNER',
        society: context.society,
      },
    ]);
    await Promise.resolve();
    http
      .expectOne('http://localhost:8081/api/v1/memberships/member-a/activate-context')
      .flush(context);
    await pending;
    expect(session.context()?.society.id).toBe('society-a');
    pending = auth.loadMemberships();
    http.expectOne('http://localhost:8081/api/v1/memberships').flush([
      {
        id: 'member-a',
        societyId: 'society-a',
        roles: 'RESIDENT_OWNER',
        society: { ...context.society, status: 'SUSPENDED' },
      },
    ]);
    await pending;
    expect(session.context()).toBeNull();
  });
  it('does not create an authenticated session for an invalid login', async () => {
    const pending = auth.login('resident@example.org', 'wrong').catch(() => undefined);
    await Promise.resolve();
    http
      .expectOne('http://localhost:8081/api/v1/auth/login')
      .flush({ message: 'Incorrect credentials' }, { status: 401, statusText: 'Unauthorized' });
    await pending;
    expect(session.token()).toBe('');
    expect(session.user()).toBeNull();
  });
  it('clears old society context before activating another membership', async () => {
    session.context.set(context);
    const generation = session.generation();
    const m: Membership = {
      id: 'membership-b',
      societyId: 'society-b',
      roles: 'WATCHMAN',
      society: { ...context.society, id: 'society-b', name: 'Society B' },
    };
    const pending = auth.switchSociety(m);
    expect(session.context()).toBeNull();
    expect(session.generation()).toBe(generation + 1);
    http.expectOne('http://localhost:8081/api/v1/memberships/membership-b/activate-context').flush({
      ...context,
      society: m.society,
      roles: ['WATCHMAN'],
      permissions: ['VISITOR_CHECKIN'],
      flats: [],
    });
    await pending;
    expect(session.can('VISITOR_READ')).toBe(false);
    expect(session.can('VISITOR_CHECKIN')).toBe(true);
    expect(sessionStorage.getItem('ms.society')).toBe('society-b');
  });
  it('revalidates a saved Super Admin society context after refresh', async () => {
    session.user.set({
      id: 'owner',
      name: 'Owner',
      email: 'owner@test.local',
      mobile: null,
      status: 'ACTIVE',
      superAdmin: true,
    });
    sessionStorage.setItem('ms.managedSociety', 'society-a');
    const pending = auth.loadMemberships();
    http.expectOne('http://localhost:8081/api/v1/memberships').flush([]);
    await Promise.resolve();
    const req = http.expectOne('http://localhost:8081/api/v1/context');
    expect(req.request.headers.get('X-Society-Id')).toBe('society-a');
    req.flush(context);
    await pending;
    expect(session.context()?.society.id).toBe('society-a');
    expect(sessionStorage.getItem('ms.managedSociety')).toBe('society-a');
    auth.logout();
    http.expectOne('http://localhost:8081/api/v1/auth/logout').flush({});
    expect(sessionStorage.getItem('ms.managedSociety')).toBeNull();
  });
  it('permission guard uses active society permissions', () => {
    session.context.set(context);
    const allowed = TestBed.runInInjectionContext(() =>
      permissionGuard({ data: { permission: 'VISITOR_READ' } } as any, {} as any),
    );
    const denied = TestBed.runInInjectionContext(() =>
      permissionGuard({ data: { permission: 'SOCIETY_UPDATE' } } as any, {} as any),
    );
    expect(allowed).toBe(true);
    expect(String(denied)).toBe('/forbidden');
  });
  it('requires a society selection and a separate platform role', () => {
    expect(String(TestBed.runInInjectionContext(() => societyGuard({} as any, {} as any)))).toBe(
      '/select-society',
    );
    expect(String(TestBed.runInInjectionContext(() => platformGuard({} as any, {} as any)))).toBe(
      '/forbidden',
    );
  });
  it('logout removes the session and increments the generation to discard stale requests', () => {
    session.setToken('test');
    session.context.set(context);
    const generation = session.generation();
    auth.logout();
    http.expectOne('http://localhost:8081/api/v1/auth/logout').flush({});
    expect(session.token()).toBe('');
    expect(session.context()).toBeNull();
    expect(sessionStorage.getItem('ms.token')).toBeNull();
    expect(session.generation()).toBe(generation + 1);
  });
});
