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
      vi
        .fn()
        .mockResolvedValue({
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
      vi
        .fn()
        .mockResolvedValue({
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
  it('stores authentication only after successful login', async () => {
    const pending = auth.login('resident@example.org', 'not-a-real-password');
    http
      .expectOne('http://localhost:8081/api/v1/auth/login')
      .flush({
        token: 'test-access-token',
        user: { id: 'u', name: 'Resident', superAdmin: false },
      });
    await Promise.resolve();
    http.expectOne('http://localhost:8081/api/v1/memberships').flush([]);
    await pending;
    expect(session.token()).toBe('test-access-token');
    expect(session.user()?.id).toBe('u');
  });
  it('does not create an authenticated session for an invalid login', async () => {
    const pending = auth.login('resident@example.org', 'wrong').catch(() => undefined);
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
    http
      .expectOne('http://localhost:8081/api/v1/memberships/membership-b/activate-context')
      .flush({
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
    expect(session.token()).toBe('');
    expect(session.context()).toBeNull();
    expect(sessionStorage.getItem('ms.token')).toBeNull();
    expect(session.generation()).toBe(generation + 1);
  });
});
