import { Component, inject, signal, OnDestroy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { SessionService } from '../core/session.service';
import { AppConfigService } from '../core/app-config.service';
import { TranslatePipe } from '../core/i18n.service';
import { Icon } from '../shared/icon';
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, TranslatePipe, Icon],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnDestroy {
  api = inject(ApiService);
  session = inject(SessionService);
  config = inject(AppConfigService);
  data = signal<any>({ stats: {} });
  error = signal('');
  loading = signal(true);
  isPlatform = !this.session.context() && this.session.user()?.superAdmin;
  private alive = true;
  private timer = setInterval(() => {
    if (document.visibilityState === 'visible') void this.load();
  }, 45000);
  constructor() {
    void this.load();
  }
  ngOnDestroy() {
    this.alive = false;
    clearInterval(this.timer);
  }
  async load() {
    const generation = this.session.generation();
    try {
      const d = await this.api.get(this.isPlatform ? '/system/diagnostics' : '/dashboard');
      if (!this.alive || generation !== this.session.generation()) return;
      this.data.set(d);
      this.error.set('');
    } catch (e) {
      if (this.alive) this.error.set(errorMessage(e));
    } finally {
      if (this.alive) this.loading.set(false);
    }
  }
  get firstName() {
    return this.session.user()?.name.split(' ')[0] || 'Neighbour';
  }
  get greeting() {
    const hour = new Date().getHours();
    return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  }
  get date() {
    return new Intl.DateTimeFormat('en-IN', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      timeZone: this.session.context()?.society.timezone || 'Asia/Kolkata',
    }).format(new Date());
  }
  get stats(): { name: string; value: any; icon: string; route: string }[] {
    if (this.isPlatform)
      return [
        {
          name: 'Total societies',
          value: this.data().totalSocieties || 0,
          icon: 'building',
          route: '/societies',
        },
        {
          name: 'Active societies',
          value: this.data().activeSocieties || 0,
          icon: 'home',
          route: '/societies',
        },
        {
          name: 'Platform users',
          value: this.data().totalUsers || 0,
          icon: 'people',
          route: '/platform-users',
        },
        {
          name: 'System health',
          value: this.data().database || 'Checking',
          icon: 'shield',
          route: '/diagnostics',
        },
      ];
    const watch = this.session.can('VISITOR_CHECKIN') && !this.session.can('COMPLAINT_ASSIGN');
    const names = watch
      ? ['Visitors inside', 'Expected today', 'Pending approvals', 'Parcels waiting']
      : this.session.can('COMPLAINT_ASSIGN')
        ? ['Total flats', 'Open complaints', 'Pending actions', 'Parcels waiting']
        : ['Pending approvals', 'Parcels waiting', 'Open complaints', 'Expected today'];
    const map: Record<string, string[]> = {
      'Total flats': ['building', 'flats'],
      'Open complaints': ['complaint', 'complaints'],
      'Pending actions': ['task', 'tasks'],
      'Parcels waiting': ['parcel', 'parcels'],
      'Visitors inside': ['visitor', 'visitors'],
      'Pending approvals': ['shield', 'visitors'],
      'Expected today': ['calendar', 'visitors'],
    };
    return names
      .filter((n) => this.data().stats?.[n] !== undefined)
      .map((n) => ({
        name: n,
        value: this.data().stats[n],
        icon: map[n][0],
        route: '/' + map[n][1],
      }));
  }
  get quickActions(): {
    label: string;
    sub: string;
    icon: string;
    route: string;
    permission?: string;
    query?: Record<string, string>;
  }[] {
    if (
      !this.isPlatform &&
      this.session.can('VISITOR_CHECKIN') &&
      !this.session.can('MEMBERSHIP_MANAGE')
    )
      return [
        {
          label: 'VISITOR',
          sub: 'Record a visitor at the gate',
          icon: 'visitor',
          route: '/visitors',
          query: { action: 'create' },
        },
        {
          label: 'EXPECTED VISITOR',
          sub: 'Find someone arriving today',
          icon: 'calendar',
          route: '/visitors',
          query: { status: 'EXPECTED' },
        },
        {
          label: 'PARCEL',
          sub: 'Receive a delivery',
          icon: 'parcel',
          route: '/parcels',
          query: { action: 'create' },
        },
        {
          label: 'CHECK OUT',
          sub: 'Record a visitor leaving',
          icon: 'visitor',
          route: '/visitors',
          query: { status: 'CHECKED_IN' },
        },
        { label: 'EMERGENCY', sub: 'Call for help', icon: 'phone', route: '/emergency-contacts' },
      ];
    return this.isPlatform
      ? [
          {
            label: 'Add a society',
            sub: 'Welcome a new community',
            icon: 'building',
            route: '/societies',
          },
          {
            label: 'Platform users',
            sub: 'Manage global accounts',
            icon: 'people',
            route: '/platform-users',
          },
          {
            label: 'Application branding',
            sub: 'Make the platform your own',
            icon: 'settings',
            route: '/branding',
          },
          {
            label: 'System diagnostics',
            sub: 'Check your connection',
            icon: 'shield',
            route: '/diagnostics',
          },
        ]
      : [
          {
            label: 'Add Visitor',
            sub: 'A smoother welcome',
            icon: 'visitor',
            route: '/visitors',
            permission: 'VISITOR_CREATE',
            query: { action: 'create' },
          },
          {
            label: 'Raise a complaint',
            sub: 'Let’s make it better',
            icon: 'complaint',
            route: '/complaints',
            permission: 'COMPLAINT_CREATE',
            query: { action: 'create' },
          },
          {
            label: 'My Parcels',
            sub: 'Your deliveries, in one place',
            icon: 'parcel',
            route: '/parcels',
            permission: 'PARCEL_READ',
          },
          {
            label: 'Emergency contacts',
            sub: 'Help when you need it',
            icon: 'phone',
            route: '/emergency-contacts',
            permission: 'EMERGENCY_READ',
          },
          {
            label: 'My Visitors',
            sub: 'Approve and track visits',
            icon: 'visitor',
            route: '/visitors',
            permission: 'VISITOR_READ',
          },
          {
            label: 'My Complaints',
            sub: 'Follow your requests',
            icon: 'complaint',
            route: '/complaints',
            permission: 'COMPLAINT_READ',
          },
          {
            label: 'Notices',
            sub: 'Community updates',
            icon: 'notice',
            route: '/notices',
            permission: 'NOTICE_READ',
          },
          {
            label: 'Meetings',
            sub: 'Dates and decisions',
            icon: 'calendar',
            route: '/meetings',
            permission: 'MEETING_READ',
          },
        ].filter((a) => this.session.can(a.permission));
  }
  dateShort(value: string) {
    return new Intl.DateTimeFormat('en-IN', {
      day: 'numeric',
      month: 'short',
      timeZone: this.session.context()?.society.timezone || 'Asia/Kolkata',
    }).format(new Date(value));
  }
  time(value: string) {
    return new Intl.DateTimeFormat('en-IN', {
      hour: 'numeric',
      minute: '2-digit',
      timeZone: this.session.context()?.society.timezone || 'Asia/Kolkata',
    }).format(new Date(value));
  }
  pretty(value: string) {
    return value?.replaceAll('_', ' ').toLowerCase() || '';
  }
}
