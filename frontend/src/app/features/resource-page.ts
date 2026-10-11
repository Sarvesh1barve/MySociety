import { Component, inject, signal, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage, downloadBlob } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { AuthService } from '../core/auth.service';
import { TranslatePipe } from '../core/i18n.service';
import { Icon } from '../shared/icon';
import { RecordForm } from '../shared/record-form';
import { MODULES, ROLES, COMPLAINT_STATUSES } from './module-definitions';
import { Row, Field, Option, ModuleDefinition } from '../core/models';
@Component({
  selector: 'app-resource-page',
  standalone: true,
  imports: [RouterLink, FormsModule, TranslatePipe, Icon, RecordForm],
  templateUrl: './resource-page.html',
})
export class ResourcePage implements OnDestroy {
  api = inject(ApiService);
  session = inject(SessionService);
  auth = inject(AuthService);
  route = inject(ActivatedRoute);
  key = '';
  def: ModuleDefinition = MODULES['visitors'];
  rows = signal<Row[]>([]);
  loading = signal(true);
  error = signal('');
  success = signal('');
  busy = signal(false);
  search = '';
  filter = '';
  page = 1;
  pageSize = 12;
  modal = signal(false);
  selected = signal<Row | null>(null);
  editing: Row | null = null;
  formFields: Field[] = [];
  formValues: Record<string, any> = {};
  formTitle = '';
  formEndpoint = '';
  formMethod = 'POST';
  formError = signal('');
  lookups: Record<string, Option[]> = {};
  lookupRows: Record<string, Row[]> = {};
  detail = signal<Row | null>(null);
  history = signal<Row[]>([]);
  comment = '';
  complaintStatus = '';
  assignee = '';
  agenda: any[] = [];
  attendance: any[] = [];
  resolutions = '';
  momStatus = 'DRAFT';
  file: File | null = null;
  fileVisibility = 'SOCIETY';
  previousVersionId = '';
  private request = 0;
  private subscription = this.route.data.subscribe((data) => {
    this.key = data['module'];
    this.def = MODULES[this.key];
    this.search = '';
    this.filter = '';
    this.page = 1;
    this.detail.set(null);
    this.modal.set(false);
    void this.load().then(() => {
      this.filter = this.route.snapshot.queryParamMap.get('status') || '';
      if (
        !this.error() &&
        this.canCreate &&
        this.route.snapshot.queryParamMap.get('action') === 'create'
      )
        this.create();
    });
  });
  private pollTimer = setInterval(() => {
    if (
      ['visitors', 'parcels'].includes(this.key) &&
      !this.modal() &&
      !this.detail() &&
      document.visibilityState === 'visible'
    )
      void this.pollRecords();
  }, 45000);
  async pollRecords() {
    const generation = this.session.generation(),
      key = this.key;
    try {
      const rows = await this.api.get<Row[]>(this.def.endpoint);
      if (generation === this.session.generation() && key === this.key) this.rows.set(rows);
    } catch {}
  }
  ngOnDestroy() {
    this.subscription.unsubscribe();
    this.request++;
    clearInterval(this.pollTimer);
  }
  async load() {
    const request = ++this.request,
      generation = this.session.generation(),
      endpoint = this.def.endpoint;
    this.loading.set(true);
    this.error.set('');
    this.rows.set([]);
    try {
      const [rows] = await Promise.all([this.api.get<Row[]>(endpoint), this.loadLookups()]);
      if (request !== this.request || generation !== this.session.generation()) return;
      this.rows.set(rows);
      this.page = Math.min(this.page, this.pageCount);
    } catch (e) {
      if (request === this.request) this.error.set(errorMessage(e));
    } finally {
      if (request === this.request) this.loading.set(false);
    }
  }
  async loadLookups() {
    this.lookups = {};
    this.lookupRows = {};
    const requests: [string, string, string | undefined][] = [
      ['flats', '/flats', 'FLAT_READ'],
      ['buildings', '/buildings', 'FLAT_READ'],
      ['wings', '/wings', 'FLAT_READ'],
      ['floors', '/floors', 'FLAT_READ'],
      ['users', '/society-users', 'RESIDENT_READ_ALL'],
      ['residents', '/residents', 'RESIDENT_READ'],
      ['meetings', '/meetings', 'MEETING_READ'],
      ['documents', '/documents', 'DOCUMENT_READ'],
    ];
    if (this.def.platform) {
      if (this.key === 'platform-users')
        requests.push(['societies', '/platform/societies', undefined]);
      else return;
    }
    await Promise.all(
      requests
        .filter(
          ([, , p]) => (!this.def.platform && this.session.can(p)) || (this.def.platform && !p),
        )
        .map(async ([name, path]) => {
          try {
            const data = await this.api.get<Row[]>(path);
            this.lookupRows[name] = data;
            this.lookups[name] = data.map((r) => ({
              value: name === 'users' ? r['user'].id : r.id,
              label:
                r['user']?.name ||
                r['name'] ||
                r['flatNumber'] ||
                r['title'] ||
                r['fileName'] ||
                r.id,
            }));
          } catch {
            /* Optional lookup remains unavailable; saving still enforces server validation. */
          }
        }),
    );
    for (const meeting of this.lookupRows['meetings'] || []) {
      try {
        this.lookups['agenda:' + meeting.id] = JSON.parse(meeting['agendaJson'] || '[]').map(
          (a: any) => ({ value: a.id, label: a.title }),
        );
      } catch {}
    }
  }
  get canCreate() {
    return this.def.fields.length > 0 || this.key === 'documents'
      ? this.def.platform
        ? true
        : !!this.def.createPermission && this.session.can(this.def.createPermission)
      : false;
  }
  get filtered() {
    const q = this.search.toLowerCase();
    return this.rows().filter(
      (r) =>
        (!this.filter || r['status'] === this.filter || r['approvalStatus'] === this.filter) &&
        (!q || this.def.columns.some((c) => this.cell(r, c.key).toLowerCase().includes(q))),
    );
  }
  get visibleRows() {
    return this.filtered.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }
  get pageCount() {
    return Math.max(1, Math.ceil(this.filtered.length / this.pageSize));
  }
  get statuses() {
    return [
      ...new Set(
        this.rows()
          .map((r) => r['status'])
          .filter(Boolean),
      ),
    ];
  }
  get isStructure() {
    return ['flats', 'buildings', 'wings', 'floors'].includes(this.key);
  }
  get isPeople() {
    return ['residents', 'mappings', 'society-users'].includes(this.key);
  }
  formatDate(value: any) {
    if (!value) return '—';
    try {
      return new Intl.DateTimeFormat('en-IN', {
        dateStyle: 'medium',
        timeStyle: String(value).includes('T') ? 'short' : undefined,
        timeZone: this.session.context()?.society.timezone || 'Asia/Kolkata',
      }).format(new Date(value));
    } catch {
      return String(value);
    }
  }
  pretty(value: any) {
    return String(value ?? '—')
      .replaceAll('_', ' ')
      .toLowerCase()
      .replace(/(^|\s)\S/g, (c) => c.toUpperCase());
  }
  cell(row: Row, key: string): string {
    let value = key.split('.').reduce((a: any, k) => a?.[k], row);
    if (value === true) return 'Yes';
    if (value === false) return 'No';
    if (value == null || value === '') return '—';
    const lookup: Record<string, string> = {
      flatId: 'flats',
      buildingId: 'buildings',
      wingId: 'wings',
      floorId: 'floors',
      assignedTo: 'users',
      residentId: 'residents',
      actorUserId: 'users',
      meetingId: 'meetings',
    };
    if (lookup[key])
      return (
        this.lookups[lookup[key]]?.find((o) => o.value === value)?.label ||
        String(value).slice(0, 8)
      );
    if (/At$|Date$|Time$|From$|Until$/.test(key)) return this.formatDate(value);
    if (
      [
        'status',
        'approvalStatus',
        'priority',
        'occupancyStatus',
        'visitorType',
        'category',
        'noticeType',
        'audience',
        'visibility',
        'momStatus',
        'relationshipType',
      ].includes(key)
    )
      return this.pretty(value);
    return String(value);
  }
  badge(key: string) {
    return [
      'status',
      'priority',
      'approvalStatus',
      'occupancyStatus',
      'momStatus',
      'visibility',
    ].includes(key);
  }
  badgeClass(value: any) {
    const v = String(value);
    return /DENIED|REJECTED|CRITICAL|SUSPENDED|CANCELLED/.test(v)
      ? 'red'
      : /PENDING|WAITING|NEW|DRAFT|HIGH|OPEN|REVIEW/.test(v)
        ? 'amber'
        : /CHECKED_OUT|CLOSED|INACTIVE|VACANT/.test(v)
          ? 'neutral'
          : 'green';
  }
  create() {
    this.selected.set(null);
    this.editing = null;
    this.formFields = this.def.fields;
    this.formValues = {};
    this.formTitle = 'Add ' + this.def.singular;
    this.formMethod = 'POST';
    this.formEndpoint = this.def.endpoint;
    this.formError.set('');
    if (this.key === 'visitors' && !this.session.can('VISITOR_READ_ALL')) {
      this.formValues = { expected: true };
      this.formFields = this.def.fields.filter((f) => f.key !== 'expected');
      this.formFields = [
        ...this.formFields,
        {
          key: 'expected',
          label: 'Pre-approve an expected visitor',
          type: 'checkbox',
          value: true,
        },
      ];
      const own = this.session.context()?.flats || [];
      this.lookups['flats'] = (this.lookupRows['flats'] || [])
        .filter((f) => own.includes(f.id))
        .map((f) => ({ value: f.id, label: f['flatNumber'] }));
    }
    this.modal.set(true);
  }
  edit(row: Row) {
    this.selected.set(null);
    this.editing = row;
    this.formTitle = 'Edit ' + this.def.singular;
    this.formFields = this.def.fields;
    this.formValues = { ...row };
    this.formMethod = 'PUT';
    this.formEndpoint = this.def.endpoint + '/' + row.id;
    this.formError.set('');
    if (this.key === 'residents')
      this.formFields = [
        ...this.def.fields,
        { key: 'active', label: 'Active', type: 'checkbox', value: true },
      ];
    if (this.key === 'platform-users')
      this.formFields = this.def.fields.filter((f) => f.key !== 'password');
    this.modal.set(true);
  }
  async save(body: Record<string, any>) {
    if (this.busy()) return;
    this.busy.set(true);
    this.formError.set('');
    try {
      if (this.key === 'visitors' && !this.editing && !this.session.can('VISITOR_READ_ALL'))
        body['expected'] = true;
      await this.api.send(this.formMethod, this.formEndpoint, body);
      this.modal.set(false);
      this.success.set('Saved successfully.');
      this.search = '';
      this.filter = '';
      this.page = 1;
      if (this.key === 'platform-users' && this.formMethod === 'POST')
        this.success.set(
          'Account created. Choose Add membership to give this person society access.',
        );
      await this.load();
    } catch (e) {
      this.formError.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async act(row: Row, action: string) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      if (action === 'download') {
        downloadBlob(await this.api.blob('/documents/' + row.id + '/download'), row['fileName']);
        return;
      }
      if (action === 'manage') {
        await this.auth.manageSociety(row.id);
        location.hash = '/dashboard';
        return;
      }
      if (action === 'end') {
        await this.api.send('PATCH', '/resident-flat-mappings/' + row.id + '/end');
      } else await this.api.send('POST', this.def.endpoint + '/' + row.id + '/' + action);
      this.success.set('Updated successfully.');
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  actions(row: Row): { key: string; label: string }[] {
    const s = this.session,
      a: { key: string; label: string }[] = [];
    if (this.key === 'visitors') {
      if (
        row['status'] === 'WAITING_APPROVAL' &&
        s.can('VISITOR_APPROVE') &&
        s.context()?.flats.includes(row['flatId'])
      )
        a.push({ key: 'approve', label: 'Allow' }, { key: 'deny', label: 'Deny' });
      if (['APPROVED', 'EXPECTED'].includes(row['status']) && s.can('VISITOR_CHECKIN'))
        a.push({ key: 'check-in', label: 'Check in' });
      if (row['status'] === 'CHECKED_IN' && s.can('VISITOR_CHECKOUT'))
        a.push({ key: 'check-out', label: 'Check out' });
      if (
        ['EXPECTED', 'WAITING_APPROVAL', 'APPROVED'].includes(row['status']) &&
        s.context()?.flats.includes(row['flatId'])
      )
        a.push({ key: 'cancel', label: 'Cancel' });
    }
    if (this.key === 'parcels' && row['status'] === 'WAITING' && s.can('PARCEL_COLLECT')) {
      a.push({ key: 'collect', label: 'Collect' });
      if (s.can('PARCEL_CREATE')) a.push({ key: 'return', label: 'Return' });
    }
    if (
      this.key === 'notices' &&
      row['acknowledgementRequired'] &&
      !row['acknowledged'] &&
      s.can('NOTICE_ACKNOWLEDGE')
    )
      a.push({ key: 'acknowledge', label: 'Acknowledge' });
    if (this.key === 'documents') a.push({ key: 'download', label: 'Download' });
    if (this.key === 'societies') a.push({ key: 'manage', label: 'Manage society' });
    if (this.key === 'mappings' && !row['endDate'] && s.can('RESIDENT_UPDATE'))
      a.push({ key: 'end', label: 'End occupancy' });
    return a;
  }
  canEdit(row: Row) {
    if (this.key === 'platform-users' && row['superAdmin']) return false;
    if (!this.def.edit) return false;
    if (this.key === 'meetings')
      return row['momStatus'] !== 'APPROVED' && this.session.can('MEETING_EDIT');
    if (this.key === 'notices')
      return new Date(row['publishDate']) > new Date() && this.session.can('NOTICE_CREATE');
    return (
      this.def.platform ||
      this.session.can(
        (
          {
            flats: 'FLAT_UPDATE',
            residents: 'RESIDENT_UPDATE',
            'emergency-contacts': 'EMERGENCY_CONTACT_MANAGE',
          } as Record<string, string>
        )[this.key],
      )
    );
  }
  async open(row: Row) {
    this.detail.set(row);
    this.formError.set('');
    this.history.set([]);
    this.comment = '';
    this.complaintStatus = row['status'];
    this.assignee = row['assignedTo'] || '';
    if (this.key === 'complaints')
      try {
        this.history.set(await this.api.get<Row[]>('/complaints/' + row.id + '/history'));
      } catch (e) {
        this.formError.set(errorMessage(e));
      }
    if (this.key === 'meetings') {
      this.agenda = JSON.parse(row['agendaJson'] || '[]');
      this.attendance = JSON.parse(row['attendanceJson'] || '[]');
      this.resolutions = row['resolutions'] || '';
      this.momStatus = row['momStatus'] || 'DRAFT';
    }
  }
  async complaintUpdate(mode: string) {
    const row = this.detail();
    if (!row || this.busy() || !this.comment.trim()) return;
    this.busy.set(true);
    this.formError.set('');
    try {
      if (mode === 'comment')
        await this.api.send('POST', '/complaints/' + row.id + '/comments', {
          comment: this.comment,
        });
      else if (mode === 'reopen')
        await this.api.send('POST', '/complaints/' + row.id + '/reopen', { comment: this.comment });
      else
        await this.api.send('PATCH', '/complaints/' + row.id, {
          status: this.complaintStatus,
          assignedTo: this.assignee,
          comment: this.comment,
        });
      await this.load();
      await this.open(this.rows().find((r) => r.id === row.id)!);
    } catch (e) {
      this.formError.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  get complaintStatuses() {
    return COMPLAINT_STATUSES;
  }
  membership(row: Row) {
    this.formTitle = 'Edit membership';
    this.formFields = [
      { key: 'roles', label: 'Roles', type: 'multi', options: ROLES, required: true },
      {
        key: 'status',
        label: 'Status',
        type: 'select',
        options: ['PENDING', 'ACTIVE', 'SUSPENDED', 'INACTIVE', 'LEFT'],
      },
    ];
    this.formValues = row;
    this.formMethod = 'PUT';
    this.formEndpoint = '/society-memberships/' + row.id;
    this.formError.set('');
    this.modal.set(true);
  }
  linkUser(row: Row) {
    this.formTitle = 'Add society membership';
    this.formFields = [
      { key: 'societyId', label: 'Society', type: 'select', lookup: 'societies', required: true },
      { key: 'roles', label: 'Roles', type: 'multi', options: ROLES, required: true },
    ];
    this.formValues = { userId: row.id };
    this.selected.set(row);
    this.formError.set('');
    this.modal.set(true);
  }
  async saveLink(body: Record<string, any>) {
    this.busy.set(true);
    this.formError.set('');
    try {
      await this.api.send(
        'POST',
        '/society-memberships',
        {
          userId: this.selected()!.id,
          roles: body['roles'],
        },
        body['societyId'],
      );
      this.modal.set(false);
      this.selected.set(null);
      this.success.set('Membership created.');
      this.search = '';
      this.filter = '';
      this.page = 1;
      await this.load();
    } catch (e) {
      this.formError.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async toggleUser(row: Row) {
    this.busy.set(true);
    try {
      await this.api.send('PATCH', '/platform/users/' + row.id, {
        status: row['status'] === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
      });
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  resetUserPassword(row: Row) {
    this.selected.set(null);
    this.editing = null;
    this.formTitle = 'Reset password for ' + row['name'];
    this.formValues = {};
    this.formFields = [
      {
        key: 'currentPassword',
        label: 'Your Super Admin password',
        type: 'password',
        required: true,
        max: 72,
        hint: 'Verify this user’s identity first. This revokes their sessions and recovery codes.',
      },
      {
        key: 'newPassword',
        label: 'Temporary password for the user',
        type: 'password',
        required: true,
        max: 72,
        hint: 'At least 12 characters. Share privately and ask the user to change it and save new recovery codes.',
      },
    ];
    this.formEndpoint = '/auth/admin-reset/' + row.id;
    this.formMethod = 'POST';
    this.formError.set('');
    this.modal.set(true);
  }
  taskStatus(row: Row) {
    this.formTitle = 'Update action';
    this.formFields = [
      {
        key: 'status',
        label: 'Status',
        type: 'select',
        options: ['OPEN', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED'],
        required: true,
      },
    ];
    this.formValues = row;
    this.formMethod = 'PATCH';
    this.formEndpoint = '/tasks/' + row.id;
    this.formError.set('');
    this.modal.set(true);
  }
  addAgenda() {
    this.agenda = [
      ...this.agenda,
      {
        id: crypto.randomUUID(),
        sequenceNumber: this.agenda.length + 1,
        title: '',
        description: '',
        discussion: '',
        decision: '',
      },
    ];
  }
  moveAgenda(index: number, direction: number) {
    const target = index + direction;
    if (target < 0 || target >= this.agenda.length) return;
    [this.agenda[index], this.agenda[target]] = [this.agenda[target], this.agenda[index]];
    this.agenda = [...this.agenda];
  }
  addAttendance() {
    this.attendance = [...this.attendance, { userId: '', status: 'PRESENT' }];
  }
  async saveMom() {
    if (this.busy() || !this.detail()) return;
    this.busy.set(true);
    this.formError.set('');
    try {
      const result = await this.api.send<Row>('PUT', '/meetings/' + this.detail()!.id + '/mom', {
        agendaJson: JSON.stringify(this.agenda.map((a, i) => ({ ...a, sequenceNumber: i + 1 }))),
        attendanceJson: JSON.stringify(this.attendance),
        resolutions: this.resolutions,
        momStatus: this.momStatus,
      });
      this.detail.set(result);
      this.success.set('Minutes saved.');
      await this.load();
    } catch (e) {
      this.formError.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async approveMom() {
    if (this.busy() || !this.detail()) return;
    this.busy.set(true);
    try {
      this.detail.set(
        await this.api.send<Row>('POST', '/meetings/' + this.detail()!.id + '/approve-mom'),
      );
      await this.load();
    } catch (e) {
      this.formError.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  print() {
    window.print();
  }
  attachments(row: Row): Option[] {
    const ids = String(row['attachmentIds'] || row['attachmentId'] || '')
      .split(',')
      .filter(Boolean);
    return ids.map((id) => ({
      value: id,
      label: this.lookups['documents']?.find((d) => d.value === id)?.label || 'Attached document',
    }));
  }
  async downloadAttachment(attachment: Option) {
    try {
      downloadBlob(
        await this.api.blob('/documents/' + attachment.value + '/download'),
        attachment.label,
      );
    } catch (e) {
      this.formError.set(errorMessage(e));
    }
  }
  fileSelected(event: Event) {
    this.file = (event.target as HTMLInputElement).files?.[0] || null;
  }
  async upload() {
    if (!this.file || this.busy()) return;
    this.busy.set(true);
    this.formError.set('');
    const body = new FormData();
    body.append('file', this.file);
    body.append('visibility', this.fileVisibility);
    if (this.previousVersionId) body.append('previousVersionId', this.previousVersionId);
    try {
      await this.api.send('POST', '/documents', body);
      this.modal.set(false);
      this.file = null;
      await this.load();
      this.success.set('Document uploaded.');
    } catch (e) {
      this.formError.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  detailFields(row: Row) {
    return Object.entries(row).filter(
      ([key, value]) =>
        ![
          'id',
          'societyId',
          'version',
          'agendaJson',
          'attendanceJson',
          'mappings',
          'user',
          'password',
          'oldValueJson',
          'newValueJson',
        ].includes(key) &&
        value != null &&
        typeof value !== 'object',
    );
  }
  closeModal() {
    if (!this.busy()) {
      this.modal.set(false);
      this.selected.set(null);
    }
  }
}
