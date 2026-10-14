import { test, expect, Page, chromium } from '@playwright/test';
import { mkdtemp, mkdir, rm } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { configureRuntime, isolated } from './isolated';

const hosted = process.env['HOSTED_URL'];
const base = hosted || process.env['E2E_BASE_URL'] || 'http://localhost:4200/';
const url = (route: string) => base.replace(/\/?$/, '/') + '#/' + route;
const privateEnv: Record<string, string> = {};
if (hosted) {
  for (const line of readFileSync(resolve('../.env'), 'utf8').split(/\r?\n/)) {
    const index = line.indexOf('=');
    if (index > 0) privateEnv[line.slice(0, index)] = line.slice(index + 1);
  }
}
const ownerEmail = hosted
  ? privateEnv['INITIAL_SUPER_ADMIN_EMAIL']
  : process.env['E2E_OWNER_EMAIL']!;
const password = hosted ? privateEnv['INITIAL_SUPER_ADMIN_PASSWORD'] : process.env['E2E_PASSWORD']!;
test.skip(!hosted && !isolated, 'Use the isolated runner or explicit hosted verification.');
async function prepare(page: Page) {
  if (!hosted) await configureRuntime(page);
}
async function login(page: Page, email: string) {
  await prepare(page);
  await page.goto(url('login'));
  await page.getByLabel('Email or mobile number').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).not.toHaveURL(/#\/login$/);
}

test('persistent HttpOnly session survives actual browser close/relaunch and restores owner context', async () => {
  test.setTimeout(120000);
  const root = resolve('../.local/browser-profiles');
  await mkdir(root, { recursive: true });
  const profile = await mkdtemp(root + sep + 'session-');
  let context = await chromium.launchPersistentContext(profile, {
    headless: true,
    serviceWorkers: isolated ? 'block' : 'allow',
  });
  try {
    let page = await context.newPage();
    await login(page, ownerEmail);
    await expect(page).toHaveURL(/platform/);
    await page.goto(url('societies'));
    await page.getByRole('button', { name: 'Manage society', exact: true }).first().click();
    await expect(page).toHaveURL(/dashboard/);
    const society = await page.locator('.topbar-left').innerText();
    await page.reload();
    await expect(page.locator('.topbar-left')).toHaveText(society, { useInnerText: true });
    const cookie = (await context.cookies()).find((c) => c.name === '__Host-ms-session');
    expect(!!cookie).toBe(true);
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.secure).toBe(true);
    expect(cookie?.sameSite).toBe('None');
    expect(cookie?.expires).toBeGreaterThan(Date.now() / 1000);
    expect(await page.evaluate(() => document.cookie.includes('__Host-ms-session'))).toBe(false);
    expect(await page.evaluate(() => sessionStorage.getItem('ms.token'))).toBeNull();
    expect(
      await page.evaluate(() => Object.values(localStorage).some((v) => v.startsWith('eyJ'))),
    ).toBe(false);
    await context.close();
    context = await chromium.launchPersistentContext(profile, {
      headless: true,
      serviceWorkers: isolated ? 'block' : 'allow',
    });
    page = await context.newPage();
    await prepare(page);
    await page.goto(url(''));
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.locator('.topbar-left')).toHaveText(society, { useInnerText: true });
    await page.goto(url('profile'));
    await expect(page.getByRole('heading', { name: 'Your profile', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page).toHaveURL(/login/);
    await expect
      .poll(async () => (await context.cookies()).some((c) => c.name === '__Host-ms-session'))
      .toBe(false);
    await page.reload();
    await expect(page).toHaveURL(/login/);
  } finally {
    await context.close();
    if (!profile.startsWith(root + sep + 'session-'))
      throw new Error('Unexpected test profile path');
    await rm(profile, { recursive: true, force: true });
  }
});

test('temporary API outage on reload preserves session then restores access; real 401 clears it', async ({
  page,
}) => {
  await login(page, ownerEmail);
  await expect(page).toHaveURL(/platform/);
  await page.route('**/api/v1/auth/refresh', (route) => route.abort('connectionfailed'));
  await page.reload();
  await expect(page).toHaveURL(/reconnect/);
  await expect(
    page.getByRole('heading', { name: 'Unable to connect to the Society server.' }),
  ).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('ms.session'))).toBe('active');
  await page.unroute('**/api/v1/auth/refresh');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page).toHaveURL(/platform/);
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({ status: 401, json: { message: 'Session revoked.' } }),
  );
  await page.reload();
  await expect(page).toHaveURL(/login/);
  expect(await page.evaluate(() => localStorage.getItem('ms.session'))).toBeNull();
});

test('resident and multi-society context persist across reopening without gaining permissions', async ({
  browser,
}) => {
  test.skip(!!hosted, 'Do not create or assume demo users in the live database.');
  for (const email of ['resident@demo.local', 'secretary@demo.local']) {
    const context = await browser.newContext({ serviceWorkers: 'block' });
    let page = await context.newPage();
    await login(page, email);
    if (email.startsWith('secretary')) {
      await page.getByRole('button', { name: /Sai Darshan Demo CHS/ }).click();
    }
    await expect(page).toHaveURL(/dashboard/);
    const society = await page.locator('.topbar-left').innerText();
    await page.close();
    page = await context.newPage();
    await prepare(page);
    await page.goto(url(''));
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.locator('.topbar-left')).toHaveText(society, { useInnerText: true });
    await expect(page.getByRole('link', { name: 'Society settings', exact: true })).toHaveCount(0);
    await page.goto(url('visitors'));
    await expect(page).toHaveURL(/visitors/);
    await page.reload();
    await expect(page).toHaveURL(/visitors/);
    await context.close();
  }
});

test('offline logout remains logged out on reload and clears cookie when connectivity returns', async ({
  page,
  context,
}) => {
  await login(page, ownerEmail);
  await expect(page).toHaveURL(/platform/);
  await page.route('**/api/v1/auth/logout', (route) => route.abort('connectionfailed'));
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page).toHaveURL(/login/);
  await page.reload();
  await expect(page).toHaveURL(/login/);
  expect(await page.evaluate(() => localStorage.getItem('ms.session'))).toBe('logout');
  await page.unroute('**/api/v1/auth/logout');
  await page.reload();
  await expect(page).toHaveURL(/login/);
  await expect
    .poll(async () => (await context.cookies()).some((c) => c.name === '__Host-ms-session'))
    .toBe(false);
});
