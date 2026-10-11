import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const hostedUrl = process.env['HOSTED_URL'];
const localEnv: Record<string, string> = {};
try {
  for (const line of readFileSync(resolve(process.cwd(), '../.env'), 'utf8').split(/\r?\n/)) {
    const index = line.indexOf('=');
    if (index > 0) localEnv[line.slice(0, index)] = line.slice(index + 1);
  }
} catch {}
const password = process.env['HOSTED_OWNER_PASSWORD'] || localEnv['INITIAL_SUPER_ADMIN_PASSWORD'];

test('hosted Pages authenticates through HTTPS and refreshes a protected hash route', async ({
  page,
}) => {
  test.skip(
    !hostedUrl || !password || process.env['HOSTED_LOGIN_CHECK'] !== '1',
    'Provide HOSTED_URL, owner credentials and HOSTED_LOGIN_CHECK=1 for this read-only check.',
  );
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(hostedUrl! + '#/login');
  const runtime = await page.evaluate(async () => {
    const response = await fetch(new URL('runtime-config.json', document.baseURI), {
      cache: 'no-store',
    });
    return response.json();
  });
  expect(runtime.apiBaseUrl).toMatch(/^https:\/\//);
  await page
    .getByLabel('Email or mobile number')
    .fill(process.env['HOSTED_OWNER_EMAIL'] || localEnv['INITIAL_SUPER_ADMIN_EMAIL']);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  await expect(
    page.getByText('All rights reserved by Sarvesh Barve', { exact: true }),
  ).toBeVisible();
  await page.getByRole('link', { name: 'Platform users', exact: true }).first().click();
  await expect(page).toHaveURL(/#\/platform-users$/);
  await expect(page.getByRole('heading', { name: 'Platform users', exact: true })).toBeVisible();
  await page.reload();
  await expect(page).toHaveURL(/#\/platform-users$/);
  await expect(page.getByRole('heading', { name: 'Platform users', exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});

test('deployed signup route and central runtime URL are current', async ({ page }) => {
  test.skip(!hostedUrl, 'Provide HOSTED_URL.');
  await page.goto(hostedUrl! + '#/login');
  await page.getByRole('link', { name: 'Create Account', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Create Account', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create Account', exact: true })).toBeVisible();
  const runtime = await page.evaluate(async () =>
    (await fetch(new URL('runtime-config.json', document.baseURI), { cache: 'no-store' })).json(),
  );
  expect(runtime.apiBaseUrl).toBe(process.env['EXPECTED_API_URL']);
  expect(runtime.ngrokSkipBrowserWarning).toBe(true);
});

test('hosted Pages PWA is installable and its shell survives offline', async ({
  page,
  context,
}) => {
  test.skip(!hostedUrl, 'Provide HOSTED_URL for this opt-in deployment check.');
  test.setTimeout(90000);
  await page.goto(hostedUrl! + '#/login');
  await expect(page.getByRole('heading', { name: 'A little closer starts here.' })).toBeVisible();
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  const client = await context.newCDPSession(page);
  expect((await client.send('Page.getAppManifest')).errors).toEqual([]);
  expect((await client.send('Page.getInstallabilityErrors')).installabilityErrors).toEqual([]);
  const cached = await page.evaluate(async () => {
    const urls: string[] = [];
    for (const name of await caches.keys()) {
      for (const request of await (await caches.open(name)).keys()) urls.push(request.url);
    }
    return urls;
  });
  expect(
    cached.some((url) => url.includes('/api/v1/') || url.includes('runtime-config.json')),
  ).toBe(false);
  await page.reload();
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'A little closer starts here.' })).toBeVisible();
  await expect(page.locator('.login-top')).toContainText(/Offline|Server unavailable/);
  await context.setOffline(false);
});
