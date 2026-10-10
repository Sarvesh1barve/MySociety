import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { SessionService } from '../core/session.service';
import { errorMessage } from '../core/api.service';
import { Membership } from '../core/models';
import { Icon } from '../shared/icon';
import { TranslatePipe } from '../core/i18n.service';
@Component({
  selector: 'app-select-society',
  standalone: true,
  imports: [RouterLink, Icon, TranslatePipe],
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
          <h3>No active memberships yet</h3>
          <p>Ask your society administrator to activate your membership.</p>
        </div>
      }
    </div>
    @if (session.user()?.superAdmin) {
      <a routerLink="/societies" class="button primary">Manage platform societies</a>
    }
  `,
})
export class SelectSociety {
  auth = inject(AuthService);
  session = inject(SessionService);
  router = inject(Router);
  busy = signal(false);
  error = signal('');
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
