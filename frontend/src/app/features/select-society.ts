import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { SessionService } from '../core/session.service';
import { errorMessage } from '../core/api.service';
import { Membership } from '../core/models';
import { Icon } from '../shared/icon';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { TranslatePipe } from '../core/i18n.service';
@Component({
  selector: 'app-select-society',
  standalone: true,
  imports: [RouterLink, Icon, TranslatePipe, FormsModule],
  template: `
    <section class="page-heading">
      <div>
        <p class="eyebrow">{{ 'YOUR COMMUNITIES' | t }}</p>
        <h1>{{ 'Where would you like to go?' | t }}</h1>
        <p class="subtitle">
          {{ 'Choose a society to continue. You can switch at any time.' | t }}
        </p>
      </div>
    </section>
    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }
    <div class="society-grid">
      @for (member of session.memberships(); track member.id) {
        <button
          class="society-card panel"
          [disabled]="busy() || !['ACTIVE', 'TRIAL'].includes(member.society.status)"
          (click)="choose(member)"
        >
          <div class="society-card-icon"><app-icon name="building" /></div>
          <span class="badge green">{{ member.society.status }}</span>
          <h2>{{ member.society.name }}</h2>
          <p>{{ member.society.city || member.society.code }}</p>
          <small>{{ member.roles.replaceAll('_', ' ').replaceAll(',', ' · ') }}</small
          ><span class="society-enter">{{ 'Enter society' | t }}<app-icon name="arrow" /></span>
        </button>
      } @empty {
        <div class="panel empty-state">
          <app-icon name="people" />
          <h3>Your account is ready</h3>
          <p>
            You don't have approved society access yet. Request access below, or ask your
            administrator about a pending or suspended membership.
          </p>
        </div>
      }
    </div>
    @if (session.user()?.superAdmin) {
      <a routerLink="/societies" class="button primary">Manage platform societies</a>
    } @else {
      <section class="panel onboarding-panel">
        <h2>Join your society</h2>
        <p>
          Ask your administrator for the society code. Your account cannot access society
          information until they approve your request.
        </p>
        <form (ngSubmit)="requestAccess()" class="record-form">
          <label
            >Society code *<input
              name="code"
              [(ngModel)]="code"
              maxlength="30"
              placeholder="For example: JB"
              required
          /></label>
          <label
            >Your building and flat *<input
              name="flatHint"
              [(ngModel)]="flatHint"
              maxlength="120"
              placeholder="For example: Wing A, Flat 101"
              required
          /></label>
          <button class="button primary" [disabled]="busy() || !code.trim() || !flatHint.trim()">
            {{ busy() ? 'Please wait…' : 'Request access' }}
          </button>
        </form>
        @if (success()) {
          <p class="alert success" role="status">{{ success() }}</p>
        }
        <h3>Your requests</h3>
        @for (r of requests(); track r.id) {
          <p>
            <strong>{{ r.societyName }}</strong> —
            {{
              r.status === 'PENDING'
                ? 'Pending Approval'
                : r.status === 'APPROVED'
                  ? 'Approved — refresh access to enter'
                  : 'Not approved'
            }}<br />{{ r.reviewNote }}
          </p>
        }
        <button class="button secondary" [disabled]="busy()" (click)="refresh()">
          Refresh access
        </button>
        <p>
          <a routerLink="/profile" class="text-link">Save your password recovery codes</a> so you
          can recover your account later.
        </p>
      </section>
    }
  `,
})
export class SelectSociety {
  auth = inject(AuthService);
  session = inject(SessionService);
  router = inject(Router);
  busy = signal(false);
  error = signal('');
  api = inject(ApiService);
  requests = signal<any[]>([]);
  success = signal('');
  code = '';
  flatHint = '';
  constructor() {
    void this.loadRequests();
  }
  async loadRequests() {
    try {
      this.requests.set(await this.api.get<any[]>('/my-membership-requests'));
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
  async requestAccess() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.send('POST', '/my-membership-requests', {
        societyCode: this.code,
        flatHint: this.flatHint,
      });
      this.success.set(
        'Your society access is pending approval. Your administrator will verify your home.',
      );
      this.code = '';
      this.flatHint = '';
      await this.loadRequests();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async refresh() {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.loadMemberships();
      await this.loadRequests();
      if (this.session.context()) await this.router.navigateByUrl('/dashboard');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
  async choose(m: Membership) {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.switchSociety(m);
      await this.router.navigateByUrl('/dashboard');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
