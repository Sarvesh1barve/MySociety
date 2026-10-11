import { Injectable, effect, inject } from '@angular/core';
import { SessionService } from './session.service';

export const THEMES = [
  { code: 'FOREST', name: 'Forest', color: '#28654e', soft: '#edf4ef' },
  { code: 'OCEAN', name: 'Ocean', color: '#126b83', soft: '#e9f5fa' },
  { code: 'INDIGO', name: 'Indigo', color: '#5048a5', soft: '#efedfb' },
  { code: 'ROSE', name: 'Rose', color: '#9b3f65', soft: '#fbeef3' },
  { code: 'AMBER', name: 'Amber', color: '#85540f', soft: '#fcf3e1' },
  { code: 'SLATE', name: 'Slate', color: '#475569', soft: '#edf1f6' },
];

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private session = inject(SessionService);
  private sync = effect(() => this.apply(this.session.context()?.society.theme || 'FOREST'));
  apply(code: string) {
    const theme = THEMES.find((t) => t.code === code) || THEMES[0];
    document.documentElement.dataset['societyTheme'] = theme.code;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme.color);
  }
}
