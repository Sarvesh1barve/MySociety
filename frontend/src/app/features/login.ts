import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormControl, FormGroup, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { AppConfigService } from '../core/app-config.service';
import { SessionService } from '../core/session.service';
import { errorMessage } from '../core/api.service';
import { Icon } from '../shared/icon';
import { TranslatePipe } from '../core/i18n.service';
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, Icon, TranslatePipe],
  template: `
    <div class="login-layout">
      <section class="login-story">
        <a class="brand brand-light" href="./"
          ><span class="brand-mark"><app-icon name="building" /></span
          >{{ config.brand().applicationName }}</a
        >
        <div class="login-story-copy">
          <p class="eyebrow">A PLACE TO BELONG</p>
          <h1>More than a society.<br />A community.</h1>
          <p>
            From a friendly welcome at the gate to the decisions that shape tomorrow. Bring your
            neighbourhood a little closer.
          </p>
          <div class="login-homes" aria-hidden="true">
            <div>⌂</div>
            <div>⌂</div>
            <div>⌂</div>
          </div>
          <span class="login-tagline"
            ><span class="tiny-dot"></span>One community. A thousand little connections.</span
          >
        </div>
        <span class="story-footer">Thoughtfully made for the place you call home.</span>
      </section>
      <section class="login-main">
        <div class="login-top">
          <span>{{ session.status() | t }}</span
          ><span class="status-dot" [class.offline]="session.status() !== 'Online'"></span>
        </div>
        <div class="login-box">
          <span class="login-icon"><app-icon name="home" /></span>
          <p class="eyebrow">{{ 'WELCOME HOME' | t }}</p>
          <h2>{{ 'A little closer starts here.' | t }}</h2>
          <p class="subtitle">{{ 'Sign in to your society account.' | t }}</p>
          @if (notice) {
            <div class="alert success" role="status">{{ notice }}</div>
          }
          @if (config.error()) {
            <div class="alert error" role="alert">{{ config.error() }}</div>
          }
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          <form [formGroup]="form" (ngSubmit)="login()">
            <label
              >{{ 'Email or mobile number' | t
              }}<input
                formControlName="login"
                autocomplete="username"
                placeholder="you@example.com"
                autofocus /></label
            ><label
              >{{ 'Password' | t }}
              <div class="password-input">
                <input
                  aria-label="Password"
                  formControlName="password"
                  [type]="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                /><button type="button" class="text-button" (click)="showPassword = !showPassword">
                  {{ showPassword ? 'Hide' : 'Show' }}
                </button>
              </div></label
            ><button
              class="button primary login-submit"
              [disabled]="busy() || form.invalid || !!config.error()"
            >
              {{ (busy() ? 'Signing in…' : 'Sign in') | t }}<app-icon name="arrow" />
            </button>
          </form>
          <a routerLink="/forgot-password" class="text-link recovery-link">Forgot password?</a>
          <p class="login-help">
            New here? <a routerLink="/signup" class="text-link">Create Account</a>
          </p>
          <div class="secure-note">
            <app-icon name="shield" />{{ 'Your community. Your privacy. Always.' | t }}
          </div>
        </div>
        <footer>All rights reserved by Sarvesh Barve</footer>
      </section>
    </div>
  `,
})
export class Login {
  auth = inject(AuthService);
  config = inject(AppConfigService);
  session = inject(SessionService);
  router = inject(Router);
  private route = inject(ActivatedRoute);
  notice = this.route.snapshot.queryParamMap.has('loginChanged')
    ? 'Login details updated. Use your new email or mobile number.'
    : this.route.snapshot.queryParamMap.has('passwordChanged')
      ? 'Password updated. Sign in with your new password.'
      : '';
  busy = signal(false);
  error = signal('');
  showPassword = false;
  form = new FormGroup({
    login: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  async login() {
    if (this.busy() || this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const { login, password } = this.form.getRawValue();
      await this.auth.login(login, password);
      await this.router.navigateByUrl(
        this.session.user()?.superAdmin && !this.session.context()
          ? '/platform'
          : this.session.context()
            ? '/dashboard'
            : '/select-society',
      );
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
