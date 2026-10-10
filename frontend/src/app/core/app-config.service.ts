import { Injectable, signal } from '@angular/core';
import { Branding } from './models';
@Injectable({ providedIn: 'root' })
export class AppConfigService {
  apiBaseUrl = '';
  ngrokSkipBrowserWarning = false;
  readonly brand = signal<Branding>({ applicationName: 'My Society', shortName: 'My Society' });
  readonly error = signal('');
  private manifestUrl = '';
  async load() {
    try {
      const response = await fetch(
        new URL('runtime-config.json?t=' + Date.now(), document.baseURI),
        { cache: 'no-store' },
      );
      if (!response.ok)
        throw new Error('Unable to load application configuration. Reconnect and try again.');
      const config = await response.json();
      const url = new URL(config.apiBaseUrl);
      if (
        !['http:', 'https:'].includes(url.protocol) ||
        url.username ||
        url.password ||
        url.search ||
        url.hash
      )
        throw new Error('Invalid API address.');
      if (location.protocol === 'https:' && url.protocol !== 'https:')
        throw new Error('The hosted application requires an HTTPS API address.');
      this.apiBaseUrl = url.href.replace(/\/$/, '');
      this.ngrokSkipBrowserWarning =
        config.ngrokSkipBrowserWarning === true &&
        /(^|\.)ngrok(-free)?\.(app|dev|io)$/.test(url.hostname);
      this.error.set('');
    } catch (error) {
      this.apiBaseUrl = '';
      this.error.set(
        error instanceof TypeError
          ? 'Unable to load application configuration. Reconnect and try again.'
          : error instanceof Error
            ? error.message
            : 'Unable to load application configuration.',
      );
    }
  }
  setBrand(brand: Branding) {
    this.brand.set(brand);
    document.title = brand.applicationName;
    if (brand.faviconUrl?.startsWith('https://'))
      document
        .querySelector<HTMLLinkElement>('link[rel="icon"]')
        ?.setAttribute('href', brand.faviconUrl);
    const base = new URL('.', document.baseURI).href;
    const manifest = {
      id: base,
      name: brand.applicationName,
      short_name: brand.shortName,
      start_url: base,
      scope: base,
      display: 'standalone',
      background_color: '#f6f8f5',
      theme_color: '#28654e',
      icons: [192, 512].map((size) => ({
        src: new URL('icons/icon-' + size + '.png', base).href,
        sizes: size + 'x' + size,
        type: 'image/png',
        purpose: size === 512 ? 'any maskable' : 'any',
      })),
    };
    if (this.manifestUrl) URL.revokeObjectURL(this.manifestUrl);
    if (typeof URL.createObjectURL === 'function') {
      this.manifestUrl = URL.createObjectURL(
        new Blob([JSON.stringify(manifest)], { type: 'application/manifest+json' }),
      );
      document
        .querySelector<HTMLLinkElement>('link[rel="manifest"]')
        ?.setAttribute('href', this.manifestUrl);
    }
  }
}
