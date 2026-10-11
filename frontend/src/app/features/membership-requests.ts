import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { SessionService } from '../core/session.service';
@Component({
  selector: 'app-membership-requests',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: ` <section class="page-heading">
      <div>
        <p class="eyebrow">SOCIETY ADMINISTRATION</p>
        <h1>Join requests</h1>
        <p>Verify each person and their home before approving access.</p>
      </div>
      <button class="button secondary" (click)="load()" [disabled]="busy()">Refresh</button>
    </section>
    <p><a routerLink="/society-users" class="text-link">Manage existing members</a></p>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    @if (success()) {
      <div class="alert success" role="status">{{ success() }}</div>
    }
    @if (loading()) {
      <p role="status">Loading requests…</p>
    }
    <div class="request-grid">
      @for (r of rows(); track r.id) {
        <article class="panel onboarding-panel">
          <span class="badge" [class.amber]="r.status === 'PENDING'">{{
            r.status === 'PENDING' ? 'Pending Approval' : r.status
          }}</span>
          <h2>{{ r.user.name }}</h2>
          <p>{{ r.user.email }} · {{ r.user.mobile }}</p>
          <p>
            Requested home: <strong>{{ r.flatHint }}</strong>
          </p>
          @if (r.status === 'PENDING') {
            <label
              >Verified flat *<select [(ngModel)]="r.flatId">
                <option value="">Choose a flat</option>
                @for (f of flats(); track f.id) {
                  <option [value]="f.id">{{ f.flatNumber }}</option>
                }
              </select></label
            >
            <label
              >Resident relationship *<select [(ngModel)]="r.relationship">
                <option value="OWNER">Owner</option>
                <option value="TENANT">Tenant</option>
                <option value="FAMILY_MEMBER">Family member</option>
              </select></label
            >
            <label
              >Message to applicant<input
                [(ngModel)]="r.note"
                maxlength="500"
                placeholder="Optional explanation"
            /></label>
            @if (!flats().length) {
              <p>
                Add your society's flats before approving residents.
                <a routerLink="/flats">Manage flats</a>
              </p>
            }
            <div class="form-actions">
              <button class="button secondary" [disabled]="busy()" (click)="review(r, false)">
                Decline</button
              ><button
                class="button primary"
                [disabled]="busy() || !r.flatId"
                (click)="review(r, true)"
              >
                Approve access
              </button>
            </div>
          } @else {
            <p>{{ r.reviewNote || 'Review complete.' }}</p>
          }
        </article>
      } @empty {
        @if (!loading()) {
          <div class="panel empty-state">
            <h2>No join requests yet</h2>
            <p>
              Give residents your society code:
              <strong>{{ session.context()?.society?.code }}</strong
              >. They can sign up and request access themselves.
            </p>
          </div>
        }
      }
    </div>`,
})
export class MembershipRequests {
  api = inject(ApiService);
  session = inject(SessionService);
  rows = signal<any[]>([]);
  flats = signal<any[]>([]);
  loading = signal(true);
  busy = signal(false);
  error = signal('');
  success = signal('');
  constructor() {
    void this.load();
  }
  async load() {
    this.loading.set(true);
    this.error.set('');
    try {
      const [rows, flats] = await Promise.all([
        this.api.get<any[]>('/membership-requests'),
        this.api.get<any[]>('/flats'),
      ]);
      this.rows.set(rows.map((r) => ({ ...r, relationship: 'OWNER', flatId: '', note: '' })));
      this.flats.set(flats.filter((f) => f.active));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
  async review(r: any, approve: boolean) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.send('POST', '/membership-requests/' + r.id + '/review', {
        approve,
        flatId: approve ? r.flatId : null,
        relationship: approve ? r.relationship : null,
        note: r.note,
      });
      this.success.set(
        approve
          ? 'Access approved. The resident can now open this society.'
          : 'Request declined. The applicant can see your message.',
      );
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
