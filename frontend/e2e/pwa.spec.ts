import { test, expect } from '@playwright/test';
test('production PWA has an installable manifest and offline shell without private caching', async ({
  page,
  context,
}) => {
  test.skip(
    process.env['CHECK_PWA'] !== '1',
    'Run against the production preview with CHECK_PWA=1.',
  );
  test.setTimeout(60000);
  await page.goto('/#/login');
  await expect(page.getByRole('heading', { name: 'A little closer starts here.' })).toBeVisible();
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  const client = await context.newCDPSession(page);
  const manifest = await client.send('Page.getAppManifest');
  expect(manifest.errors).toEqual([]);
  const parsed = JSON.parse(manifest.data!);
  expect(parsed.display).toBe('standalone');
  expect(parsed.icons.length).toBeGreaterThanOrEqual(2);
  const install = await client.send('Page.getInstallabilityErrors');
  expect(install.installabilityErrors).toEqual([]);
  const cached = await page.evaluate(async () => {
    const urls: string[] = [];
    for (const name of await caches.keys()) {
      const cache = await caches.open(name);
      for (const request of await cache.keys()) urls.push(request.url);
    }
    return urls;
  });
  expect(cached.some((url) => url.includes('/api/v1/'))).toBe(false);
  expect(cached.some((url) => url.includes('runtime-config.json'))).toBe(false);
  await page.reload();
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'A little closer starts here.' })).toBeVisible();
  await expect(page.locator('.login-top')).toContainText(/Offline|Server unavailable/);
  await expect(page.getByRole('alert')).toContainText('Unable to load application configuration.');
  await context.setOffline(false);
});
