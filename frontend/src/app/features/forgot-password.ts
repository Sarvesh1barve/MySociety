import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormControl, FormGroup, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AppConfigService } from '../core/app-config.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <main class="recovery-page">
      <section class="panel settings-panel">
        <p class="eyebrow">{{ config.brand().applicationName }}</p>
        <h1>Forgot password?</h1>
        <p>
          Use one of the private recovery codes you saved from Your profile. This works for all
          accounts, including Super Admin.
        </p>
        @if (error()) {
          <div class="alert error" role="alert">{{ error() }}</div>
        }
        @if (success()) {
          <div class="alert success" role="status">{{ success() }}</div>
        } @else {
          <form [formGroup]="form" (ngSubmit)="reset()">
            <label
              >Email or mobile number<input
                formControlName="login"
                autocomplete="username"
                maxlength="254"
            /></label>
            <label
              >One-time recovery code<input
                formControlName="recoveryCode"
                autocomplete="off"
                autocapitalize="characters"
                spellcheck="false"
                maxlength="80"
            /></label>
            <label
              >New password<input
                formControlName="newPassword"
                type="password"
                autocomplete="new-password"
                minlength="12"
                maxlength="72"
            /></label>
            <label
              >Confirm new password<input
                formControlName="confirmPassword"
                type="password"
                autocomplete="new-password"
                minlength="12"
                maxlength="72"
            /></label>
            <p class="muted">
              Use 12 or more characters (at most 72 UTF-8 bytes). Your existing sessions will be
              signed out.
            </p>
            <button class="button primary" [disabled]="busy() || form.invalid || !!config.error()">
              {{ busy() ? 'Resetting…' : 'Reset password' }}
            </button>
          </form>
        }
        <a routerLink="/login" class="text-link">Back to sign in</a>
        <p class="muted">
          No saved code? Contact your platform owner. There is no email/OTP recovery or public
          bypass. Your owner must verify your identity before arranging account recovery.
        </p>
        <p class="ownership-note">All rights reserved by Sarvesh Barve</p>
      </section>
    </main>
  `,
})
export class ForgotPassword {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  config = inject(AppConfigService);
  busy = signal(false);
  error = signal('');
  success = signal('');
  form = new FormGroup({
    login: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    recoveryCode: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12)],
    }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  async reset() {
    if (this.busy() || this.form.invalid) return;
    const body = this.form.getRawValue();
    this.error.set('');
    if (body.newPassword !== body.confirmPassword) {
      this.error.set('The new passwords do not match.');
      return;
    }
    this.busy.set(true);
    try {
      const result = await this.api.send('POST', '/auth/forgot-password', {
        login: body.login,
        recoveryCode: body.recoveryCode,
        newPassword: body.newPassword,
      });
      this.auth.logout();
      this.form.reset();
      this.success.set(result.message);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
