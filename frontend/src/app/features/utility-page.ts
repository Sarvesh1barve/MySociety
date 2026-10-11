import { Component, inject, signal, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage, downloadBlob } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { AppConfigService } from '../core/app-config.service';
import { TranslatePipe } from '../core/i18n.service';
import { RecordForm } from '../shared/record-form';
import { Icon } from '../shared/icon';
import { Field, Row } from '../core/models';
import { THEMES } from '../core/theme.service';
@Component({
  selector: 'app-utility-page',
  standalone: true,
  imports: [RouterLink, FormsModule, TranslatePipe, RecordForm, Icon],
  templateUrl: './utility-page.html',
})
export class UtilityPage implements OnDestroy {
  api = inject(ApiService);
  session = inject(SessionService);
  config = inject(AppConfigService);
  route = inject(ActivatedRoute);
  auth = inject(AuthService);
  router = inject(Router);
  profileFlats = signal<string[]>([]);
  themes = THEMES;
  selectedTheme = 'FOREST';
  recoveryRemaining = signal<number | null>(null);
  recoveryCodes = signal<string[]>([]);
  recoveryPassword = '';
  loginValues: Record<string, any> = {};
  loginFields: Field[] = [
    { key: 'email', label: 'Login email', type: 'email', max: 254 },
    { key: 'mobile', label: 'Login mobile number', max: 20 },
    {
      key: 'currentPassword',
      label: 'Confirm current password',
      type: 'password',
      required: true,
      max: 72,
    },
  ];
  key = '';
  title = '';
  description = '';
  loading = signal(false);
  busy = signal(false);
  error = signal('');
  success = signal('');
  values: Record<string, any> = {};
  fields: Field[] = [];
  rows = signal<Row[]>([]);
  diagnostics = signal<any>({});
  query = '';
  report = 'flats';
  role: Row | null = null;
  permissions: string[] = [];
  selectedPermissions = new Set<string>();
  reports = [
    { value: 'flats', label: 'Flat list', permission: 'FLAT_READ' },
    { value: 'residents', label: 'Resident list', permission: 'RESIDENT_READ' },
    { value: 'visitors-today', label: 'Visitors today', permission: 'VISITOR_READ' },
    { value: 'visitors-inside', label: 'Visitors currently inside', permission: 'VISITOR_READ' },
    { value: 'complaints-open', label: 'Open complaints', permission: 'COMPLAINT_READ' },
    { value: 'actions-pending', label: 'Pending meeting actions', permission: 'TASK_READ' },
    { value: 'parcels-waiting', label: 'Waiting parcels', permission: 'PARCEL_READ' },
  ];
  private revision = 0;
  private sub = this.route.data.subscribe((d) => {
    this.key = d['page'];
    this.title = d['title'];
    this.description = d['description'] || '';
    this.rows.set([]);
    void this.load();
  });
  ngOnDestroy() {
    this.recoveryCodes.set([]);
    this.recoveryPassword = '';
    this.sub.unsubscribe();
    this.revision++;
  }
  get availableReports() {
    return this.reports.filter((r) => this.session.can(r.permission));
  }
  async load() {
    const revision = ++this.revision;
    this.error.set('');
    this.loading.set(true);
    try {
      if (this.key === 'branding') {
        this.values = await this.api.get('/application/config');
        this.fields = [
          { key: 'applicationName', label: 'Application name', required: true, max: 80 },
          { key: 'shortName', label: 'Short name', required: true, max: 30 },
          { key: 'logoUrl', label: 'Logo URL (HTTPS)', max: 1000 },
          { key: 'faviconUrl', label: 'Favicon URL (HTTPS)', max: 1000 },
          { key: 'footerText', label: 'Additional footer text', max: 200 },
          { key: 'supportEmail', label: 'Support email', type: 'email', max: 254 },
          { key: 'supportPhone', label: 'Support phone', max: 20 },
        ];
      } else if (this.key === 'settings') {
        this.values = await this.api.get('/society');
        this.selectedTheme = this.values['theme'] || 'FOREST';
        this.fields = [
          { key: 'name', label: 'Society name', required: true, max: 160 },
          { key: 'address', label: 'Address', type: 'textarea', max: 500 },
          { key: 'email', label: 'Email', type: 'email', max: 254 },
          { key: 'phone', label: 'Phone', max: 20 },
          { key: 'timezone', label: 'Timezone', required: true, max: 80 },
        ];
      } else if (this.key === 'profile') {
        this.loginValues = {
          email: this.session.user()?.email,
          mobile: this.session.user()?.mobile,
        };
        const recovery = await this.api.get('/auth/recovery-codes');
        this.recoveryRemaining.set(recovery.remaining);
        this.values = { name: this.session.user()?.name };
        this.fields = [{ key: 'name', label: 'Your name', required: true, max: 120 }];
        this.profileFlats.set([]);
        if (this.session.context()) {
          const flats = await this.api.get<Row[]>('/flats');
          this.profileFlats.set(
            flats
              .filter((f) => this.session.context()?.flats.includes(f.id))
              .map((f) => f['flatNumber']),
          );
        }
      } else if (this.key === 'diagnostics') {
        const health = await this.api.get('/system/health');
        const backend = await this.api.get('/system/diagnostics');
        this.diagnostics.set({ ...backend, health: health.status });
      } else if (this.key === 'roles') {
        const result = await this.api.get('/platform/roles');
        this.rows.set(result.roles);
        this.permissions = result.permissions.filter(
          (p: string) =>
            !['APPLICATION_SETTING_MANAGE', 'SOCIETY_CREATE', 'SOCIETY_MANAGE'].includes(p),
        );
      } else if (this.key === 'notifications')
        this.rows.set(await this.api.get<Row[]>('/notifications'));
      else if (this.key === 'reports') {
        if (!this.availableReports.some((r) => r.value === this.report))
          this.report = this.availableReports[0]?.value || '';
        if (this.report) this.rows.set(await this.api.get<Row[]>('/reports/' + this.report));
      }
    } catch (e) {
      if (revision === this.revision) this.error.set(errorMessage(e));
    } finally {
      if (revision === this.revision) this.loading.set(false);
    }
  }
  async save(body: Record<string, any>) {
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
    try {
      if (this.key === 'branding')
        this.config.setBrand(await this.api.send('PUT', '/platform/branding', body));
      if (this.key === 'settings') {
        const result = await this.api.send('PUT', '/society', body);
        const context = this.session.context();
        if (context) this.session.context.set({ ...context, society: result });
      }
      if (this.key === 'profile')
        this.session.user.set(await this.api.send('PATCH', '/auth/me', body));
      this.success.set('Changes saved.');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  passwordFields: Field[] = [
    {
      key: 'currentPassword',
      label: 'Current password',
      type: 'password',
      required: true,
      max: 72,
    },
    { key: 'newPassword', label: 'New password', type: 'password', required: true, max: 72 },
    {
      key: 'confirmPassword',
      label: 'Confirm new password',
      type: 'password',
      required: true,
      max: 72,
    },
  ];
  async changePassword(body: Record<string, any>) {
    this.error.set('');
    if (body['newPassword'] !== body['confirmPassword']) {
      this.error.set('The new passwords do not match.');
      return;
    }
    this.busy.set(true);
    try {
      await this.api.send('POST', '/auth/change-password', {
        currentPassword: body['currentPassword'],
        newPassword: body['newPassword'],
      });
      this.auth.logout();
      await this.router.navigate(['/login'], { queryParams: { passwordChanged: '1' } });
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async refreshConfig() {
    await this.config.load();
    await this.load();
  }
  async saveTheme() {
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
    try {
      const society = await this.api.send('PATCH', '/society/theme', { theme: this.selectedTheme });
      const context = this.session.context();
      if (context) this.session.context.set({ ...context, society });
      this.success.set(
        'Society theme saved. Members see it when they next open or switch to this society.',
      );
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
  async changeLogin(body: Record<string, any>) {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.send('PUT', '/auth/login-details', body);
      this.auth.logout();
      await this.router.navigate(['/login'], { queryParams: { loginChanged: '1' } });
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
  async generateRecoveryCodes() {
    if (!this.recoveryPassword || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.recoveryCodes.set([]);
    try {
      const result = await this.api.send('POST', '/auth/recovery-codes', {
        currentPassword: this.recoveryPassword,
      });
      this.recoveryCodes.set(result.codes);
      this.recoveryRemaining.set(result.remaining);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.recoveryPassword = '';
      this.busy.set(false);
    }
  }
  downloadRecoveryCodes() {
    const text = `My Society private recovery codes\nLogin: ${this.session.user()?.email || this.session.user()?.mobile}\nEach code works once. Keep these in your password manager or offline.\n\n${this.recoveryCodes().join('\n')}\n`;
    downloadBlob(new Blob([text], { type: 'text/plain' }), 'my-society-private-recovery-codes.txt');
  }
  async search() {
    if (this.query.trim().length < 2) return;
    this.loading.set(true);
    this.error.set('');
    try {
      this.rows.set(await this.api.get('/search?q=' + encodeURIComponent(this.query.trim())));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
  async export() {
    this.busy.set(true);
    try {
      downloadBlob(await this.api.blob('/reports/' + this.report + '/csv'), this.report + '.csv');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  reportColumns() {
    return this.rows().length
      ? Object.keys(this.rows()[0])
          .filter((k) => !['id', 'societyId', 'version', 'attachmentIds'].includes(k))
          .slice(0, 8)
      : [];
  }
  pretty(value: any) {
    return String(value ?? '—').replaceAll('_', ' ');
  }
  chooseRole(row: Row) {
    this.role = row;
    this.selectedPermissions = new Set(String(row['permissions']).split(','));
  }
  permissionChanged(permission: string, event: Event) {
    if ((event.target as HTMLInputElement).checked) this.selectedPermissions.add(permission);
    else this.selectedPermissions.delete(permission);
  }
  async saveRole() {
    if (!this.role) return;
    this.busy.set(true);
    try {
      await this.api.send('PUT', '/platform/roles/' + this.role.id, {
        permissions: [...this.selectedPermissions].join(','),
      });
      this.role = null;
      await this.load();
      this.success.set('Role permissions updated. New requests use the updated permissions.');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async read(row: Row) {
    try {
      await this.api.send('POST', '/notifications/' + row.id + '/read');
      this.rows.update((rows) =>
        rows.map((r) => (r.id === row.id ? { ...r, readAt: new Date().toISOString() } : r)),
      );
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
  notificationRoute(row: Row) {
    return (
      (
        {
          Visitor: '/visitors',
          Parcel: '/parcels',
          Complaint: '/complaints',
          Notice: '/notices',
          Meeting: '/meetings',
          ActionTask: '/tasks',
        } as Record<string, string>
      )[row['relatedEntityType']] || '/dashboard'
    );
  }
}
