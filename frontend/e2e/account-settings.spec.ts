import { test, expect, Page } from '@playwright/test';
import { isolated, configureRuntime } from './isolated';
const base = process.env['HOSTED_URL'] || process.env['E2E_BASE_URL'] || 'http://localhost:4200/';
const apiBase = process.env['E2E_API_URL'] || 'http://localhost:8081';
const password = process.env['E2E_PASSWORD'] || '';
test.skip(
  !isolated || !password,
  'Initialize local demo credentials before running these opt-in browser checks.',
);
test.setTimeout(90000);
async function login(page: Page, email: string, secret = password) {
  await configureRuntime(page);
  await page.goto(base.replace(/\/?$/, '/') + '#/login');
  await page.getByLabel('Email or mobile number').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(secret);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).not.toHaveURL(/#\/login$/);
}
async function visit(page: Page, route: string) {
  await page.goto(base.replace(/\/?$/, '/') + '#/' + route);
}

test('society admin saves a theme, reload persists it, and switching clears it', async ({
  page,
}) => {
  await login(page, 'secretary@demo.local');
  await page.getByRole('button', { name: /My Society Demo CHS/ }).click();
  await expect(page).toHaveURL(/dashboard/);
  try {
    await visit(page, 'settings');
    await page.getByRole('button', { name: 'Ocean', exact: true }).click();
    await page.getByRole('button', { name: 'Apply society theme' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-society-theme', 'OCEAN');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-society-theme', 'OCEAN');
    await page.screenshot({
      path: '../.local/screenshots/ocean-theme-settings.png',
      fullPage: true,
    });
    await page.locator('.society-switcher').click();
    await page.getByRole('button', { name: /Sai Darshan Demo CHS/ }).click();
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.locator('.topbar-left')).toContainText('Sai Darshan Demo CHS');
    await expect(page.locator('html')).toHaveAttribute('data-society-theme', 'FOREST');
    await expect(page.getByRole('link', { name: 'Society settings', exact: true })).toHaveCount(0);
  } finally {
    await visit(page, 'select-society');
    await page.getByRole('button', { name: /My Society Demo CHS/ }).click();
    await expect(page).toHaveURL(/dashboard/);
    await visit(page, 'settings');
    await page.getByRole('button', { name: 'Forest', exact: true }).click();
    await page.getByRole('button', { name: 'Apply society theme' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-society-theme', 'FOREST');
  }
});

test('Super Admin can choose a society theme from society editing', async ({ page }) => {
  await login(page, process.env['E2E_OWNER_EMAIL']!, process.env['E2E_OWNER_PASSWORD']!);
  await visit(page, 'societies');
  const row = page.getByRole('row').filter({ hasText: 'My Society Demo CHS' });
  await row.getByRole('button', { name: 'Edit', exact: true }).click();
  await page.getByLabel('Colour theme').selectOption('INDIGO');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await row.getByRole('button', { name: 'Manage society', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-society-theme', 'INDIGO');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-society-theme', 'INDIGO');
  await visit(page, 'settings');
  await page.getByRole('button', { name: 'Forest', exact: true }).click();
  await page.getByRole('button', { name: 'Apply society theme' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-society-theme', 'FOREST');
});

test('user saves recovery codes, resets password once, and changes login identity', async ({
  page,
  request,
}) => {
  const ownerResponse = await request.post(apiBase + '/api/v1/auth/login', {
    data: {
      login: process.env['E2E_OWNER_EMAIL']!,
      password: process.env['E2E_OWNER_PASSWORD']!,
    },
    headers: { 'ngrok-skip-browser-warning': 'true' },
  });
  expect(ownerResponse.ok()).toBe(true);
  const owner = await ownerResponse.json();
  const email = `browser-recovery-${Date.now()}@test.local`;
  const original = 'Browser-Initial-' + crypto.randomUUID();
  const created = await request.post(apiBase + '/api/v1/platform/users', {
    headers: { Authorization: 'Bearer ' + owner.token, 'ngrok-skip-browser-warning': 'true' },
    data: { name: 'Browser Recovery Test', email, password: original },
  });
  expect(created.ok()).toBe(true);
  await login(page, email, original);
  await visit(page, 'profile');
  await page.getByLabel('Recovery confirmation password').fill(original);
  await page.getByRole('button', { name: 'Generate new recovery codes' }).click();
  await expect(page.locator('.recovery-code-panel code')).toHaveCount(5);
  const code = (await page.locator('.recovery-code-panel code').first().textContent())!;
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await page.getByRole('link', { name: 'Forgot password?' }).click();
  await page.getByLabel('Email or mobile number').fill(email);
  await page.getByLabel('One-time recovery code').fill(code);
  let updated = 'Browser-Updated-' + crypto.randomUUID();
  await page.getByLabel('New password', { exact: true }).fill(updated);
  await page.getByLabel('Confirm new password', { exact: true }).fill(updated);
  const recovered = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v1/auth/forgot-password') &&
      response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Reset password', exact: true }).click();
  const response = await recovered;
  expect(response.status()).toBe(200);
  expect((await response.json()).message).toContain('Password reset.');
  await expect(page.getByRole('status')).toContainText('Password reset.');
  await login(page, email, updated);
  await visit(page, 'profile');
  await expect(page.getByText('Unused codes: 4')).toBeVisible();
  await expect(page.getByText('No society role yet', { exact: true })).toBeVisible();
  const inputIds = await page
    .locator('app-record-form input[id]')
    .evaluateAll((inputs) => inputs.map((input) => input.id));
  expect(new Set(inputIds).size).toBe(inputIds.length);
  const passwordSection = page
    .locator('section')
    .filter({ has: page.getByRole('heading', { name: 'Change password', exact: true }) });
  await passwordSection.getByLabel('Current password', { exact: false }).fill(updated);
  updated = 'Browser-Changed-' + crypto.randomUUID();
  await passwordSection.getByLabel('New password', { exact: false }).first().fill(updated);
  await passwordSection.getByLabel('Confirm new password', { exact: false }).fill(updated);
  await passwordSection.getByRole('button', { name: 'Change password', exact: true }).click();
  await expect(page).toHaveURL(/passwordChanged/);
  await login(page, email, updated);
  await visit(page, 'profile');
  const changedEmail = email.replace('browser-recovery-', 'browser-renamed-');
  await page.getByLabel('Login email', { exact: false }).fill(changedEmail);
  await page.getByLabel('Confirm current password', { exact: false }).fill(updated);
  await page.getByRole('button', { name: 'Update login details', exact: true }).click();
  await expect(page).toHaveURL(/loginChanged/);
  await login(page, changedEmail, updated);
  await visit(page, 'profile');
  await expect(page.getByLabel('Login email', { exact: false })).toHaveValue(changedEmail);
});
