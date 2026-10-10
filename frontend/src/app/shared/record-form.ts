import { Component, Input, Output, EventEmitter, OnChanges } from '@angular/core';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { Field, Option } from '../core/models';
import { TranslatePipe } from '../core/i18n.service';
@Component({
  selector: 'app-record-form',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  template: ` <form [formGroup]="form" (ngSubmit)="submit()" class="record-form">
    <div class="form-grid">
      @for (field of fields; track field.key) {
        @if (visible(field)) {
          <label
            [class.wide]="
              field.type === 'textarea' || field.type === 'multi' || field.type === 'checkbox'
            "
            [class.checkbox-field]="field.type === 'checkbox'"
          >
            @if (field.type !== 'checkbox') {
              <span
                >{{ field.label | t }}
                @if (field.required) {
                  <span class="required">*</span>
                }
              </span>
            }
            @switch (field.type) {
              @case ('textarea') {
                <textarea
                  [formControlName]="field.key"
                  rows="4"
                  [maxlength]="field.max || 12000"
                ></textarea>
              }
              @case ('select') {
                <select [formControlName]="field.key">
                  <option value="">— {{ 'Select' | t }} —</option>
                  @for (option of options(field); track option.value) {
                    <option [value]="option.value">{{ option.label | t }}</option>
                  }
                </select>
              }
              @case ('multi') {
                <select [formControlName]="field.key" multiple size="5">
                  @for (option of options(field); track option.value) {
                    <option [value]="option.value">{{ option.label | t }}</option>
                  }</select
                ><small>{{ 'Hold Ctrl or Command to select more than one.' | t }}</small>
              }
              @case ('checkbox') {
                <input type="checkbox" [formControlName]="field.key" /><span>{{
                  field.label | t
                }}</span>
              }
              @default {
                <input
                  [type]="field.type || 'text'"
                  [formControlName]="field.key"
                  [maxlength]="field.max || 500"
                  [attr.min]="field.type === 'number' ? 0 : null"
                  [attr.autocomplete]="field.type === 'password' ? 'new-password' : null"
                />
              }
            }
            @if (field.hint) {
              <small>{{ field.hint | t }}</small>
            }
            @if (form.controls[field.key].invalid && form.controls[field.key].touched) {
              <small class="field-error">{{ 'Please enter a valid value.' | t }}</small>
            }
          </label>
        }
      }
    </div>
    @if (error) {
      <div class="alert error" role="alert">{{ error }}</div>
    }
    <div class="form-actions">
      <button type="button" class="button secondary" (click)="cancelled.emit()" [disabled]="busy">
        {{ 'Cancel' | t }}</button
      ><button type="submit" class="button primary" [disabled]="busy || form.invalid">
        {{ (busy ? 'Saving…' : submitLabel) | t }}
      </button>
    </div>
  </form>`,
})
export class RecordForm implements OnChanges {
  @Input() fields: Field[] = [];
  @Input() values: Record<string, any> = {};
  @Input() lookups: Record<string, Option[]> = {};
  @Input() busy = false;
  @Input() error = '';
  @Input() submitLabel = 'Save';
  @Output() submitted = new EventEmitter<Record<string, any>>();
  @Output() cancelled = new EventEmitter<void>();
  form = new FormGroup<Record<string, FormControl<any>>>({});
  ngOnChanges(changes: any) {
    if (!changes.fields && !changes.values) return;
    const controls: Record<string, FormControl<any>> = {};
    for (const f of this.fields) {
      let v =
        this.values[f.key] ??
        f.value ??
        (f.type === 'checkbox' ? false : f.type === 'multi' ? [] : '');
      if (f.type === 'multi' && typeof v === 'string') v = v ? v.split(',') : [];
      if (f.type === 'datetime-local' && v) {
        const d = new Date(v);
        v = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
      }
      const validators = [];
      if (f.required) validators.push(Validators.required);
      if (f.max) validators.push(Validators.maxLength(f.max));
      if (f.type === 'email') validators.push(Validators.email);
      if (f.type === 'password') validators.push(Validators.minLength(12));
      controls[f.key] = new FormControl(v, validators);
    }
    this.form = new FormGroup(controls);
  }
  visible(f: Field) {
    if (['expectedFrom', 'expectedUntil'].includes(f.key)) return !!this.form.value['expected'];
    if (f.key === 'targetIds') return String(this.form.value['audience']).startsWith('SPECIFIC');
    return true;
  }
  options(f: Field): Option[] {
    let lookup = f.lookup;
    if (lookup === 'targets')
      lookup = (
        { SPECIFIC_WING: 'wings', SPECIFIC_FLOOR: 'floors', SPECIFIC_FLATS: 'flats' } as Record<
          string,
          string
        >
      )[this.form.value['audience']];
    if (lookup === 'agenda') lookup = 'agenda:' + this.form.value['meetingId'];
    return lookup
      ? this.lookups[lookup] || []
      : (f.options || []).map((value) => ({ value, label: value.replaceAll('_', ' ') }));
  }
  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy) return;
    const data: Record<string, any> = { ...this.form.getRawValue() };
    for (const f of this.fields) {
      if (!this.visible(f)) {
        data[f.key] = null;
        continue;
      }
      if (f.type === 'multi') data[f.key] = (data[f.key] || []).join(',');
      if (f.type === 'datetime-local')
        data[f.key] = data[f.key] ? new Date(data[f.key]).toISOString() : null;
      if (f.type === 'number') data[f.key] = data[f.key] === '' ? null : Number(data[f.key]);
    }
    this.submitted.emit(data);
  }
}
