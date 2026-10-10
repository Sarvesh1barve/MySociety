import { Injectable, Pipe, PipeTransform, inject, signal } from '@angular/core';
@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly language = signal(localStorage.getItem('ms.language') || 'en');
  readonly dictionary = signal<Record<string, string>>({});
  async load(language = this.language()) {
    if (!['en', 'mr', 'hi'].includes(language)) language = 'en';
    try {
      const r = await fetch(new URL('i18n/' + language + '.json', document.baseURI));
      this.dictionary.set(await r.json());
      this.language.set(language);
      localStorage.setItem('ms.language', language);
    } catch {
      this.dictionary.set({});
    }
  }
  t(key: string) {
    return this.dictionary()[key] || key;
  }
}
@Pipe({ name: 't', pure: false, standalone: true })
export class TranslatePipe implements PipeTransform {
  private i18n = inject(I18nService);
  transform(key: string) {
    return this.i18n.t(key);
  }
}
