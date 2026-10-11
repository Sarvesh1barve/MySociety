import { Page } from '@playwright/test';
export const isolated =
  process.env['E2E_ISOLATED'] === 'true' &&
  /^http:\/\/(localhost|127\.0\.0\.1):8091$/.test(process.env['E2E_API_URL'] || '');
export async function configureRuntime(page: Page) {
  if (!isolated)
    throw new Error('Mutating browser checks require the isolated test server on port 8091.');
  await page.route('**/runtime-config.json*', (route) =>
    route.fulfill({
      json: { apiBaseUrl: process.env['E2E_API_URL'], ngrokSkipBrowserWarning: false },
    }),
  );
}
