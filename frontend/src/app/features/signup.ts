import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: ` <main class="auth-simple">
    <section class="panel onboarding-panel">
      <p class="eyebrow">MY SOCIETY</p>
      <h1>Create Account</h1>
      @if (done()) {
        <div class="alert success" role="status">Account created successfully.</div>
        <h2>Your next step: join your society</h2>
        <p>
          Sign in, enter the society code provided by your administrator, and request access. Your
          administrator will verify your flat before approving you.
        </p>
        <a routerLink="/login" class="button primary">Sign In</a>
      } @else {
        <p>One account for your communities. Society access is approved separately.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" class="record-form">
          <label
            >Full name *<input
              formControlName="name"
              autocomplete="name"
              maxlength="120"
              placeholder="Your full name"
          /></label>
          <label
            >Mobile number *<input
              formControlName="mobile"
              type="tel"
              autocomplete="tel"
              maxlength="20"
              placeholder="9876543210"
            /><small>For India, enter 10 digits. Otherwise include your country code.</small></label
          >
          <label
            >Email *<input
              formControlName="email"
              type="email"
              autocomplete="email"
              maxlength="254"
              placeholder="you@example.com"
          /></label>
          <label
            >Password *<input
              formControlName="password"
              type="password"
              autocomplete="new-password"
              maxlength="72"
            /><small>Use at least 12 characters. A few memorable words work well.</small></label
          >
          <label
            >Confirm password *<input
              formControlName="confirmPassword"
              type="password"
              autocomplete="new-password"
              maxlength="72"
          /></label>
          @if (error()) {
            <div class="alert error" role="alert">{{ error() }}</div>
          }
          <button class="button primary" [disabled]="busy()">
            {{ busy() ? 'Creating account…' : 'Create Account' }}
          </button>
        </form>
        <p>
          Already registered? <a routerLink="/login" class="text-link">Sign In</a> ·
          <a routerLink="/forgot-password" class="text-link">Forgot password?</a>
        </p>
      }
    </section>
    <footer>All rights reserved by Sarvesh Barve</footer>
  </main>`,
})
export class Signup {
  api = inject(ApiService);
  busy = signal(false);
  error = signal('');
  done = signal(false);
  form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    mobile: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\+?[0-9][0-9 -]{6,18}$/)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12)],
    }),
    confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  async submit() {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    const body = this.form.getRawValue();
    if (this.form.invalid) {
      this.error.set(
        'Enter your name, a valid mobile number and email, and a password of at least 12 characters.',
      );
      return;
    }
    if (body.password !== body.confirmPassword) {
      this.error.set('Passwords do not match.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.send('POST', '/auth/signup', body);
      this.form.reset();
      this.done.set(true);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
