import { test, expect, Page } from '@playwright/test';
import { isolated, configureRuntime } from './isolated';
const password = process.env['E2E_PASSWORD'] || '';
const ownerPassword = process.env['E2E_OWNER_PASSWORD'] || '';
test.skip(
  !isolated || !password,
  'Run scripts/run-browser-tests.ps1 against the isolated test database.',
);
async function login(page: Page, email = 'resident@demo.local', pass = password) {
  await configureRuntime(page);
  await page.goto('/#/login');
  await page.getByLabel('Email or mobile number').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(pass);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).not.toHaveURL(/#\/login$/);
}
test('runtime configuration, login, resident dashboard and copyright', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await login(page);
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  await expect(
    page.getByText('All rights reserved by Sarvesh Barve', { exact: true }),
  ).toBeVisible();
  await expect(page.getByText('Aarav', { exact: false }).first()).toBeVisible();
  await page.screenshot({ path: '../.local/screenshots/resident-dashboard.png', fullPage: true });
  expect(errors).toEqual([]);
});
test('permission guard denies unauthorized admin route', async ({ page }) => {
  await login(page);
  await page.goto('/#/societies');
  await expect(page).toHaveURL(/forbidden/);
  await expect(
    page.getByRole('heading', { name: 'This area needs permission' }).first(),
  ).toBeVisible();
});
test('society switch replaces permissions and clears old records', async ({ page }) => {
  await login(page, 'secretary@demo.local');
  await expect(page.getByRole('heading', { name: 'Where would you like to go?' })).toBeVisible();
  await page.getByRole('button', { name: /My Society Demo CHS/ }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Residents', exact: true })).toBeVisible();
  await page.screenshot({ path: '../.local/screenshots/committee-dashboard.png', fullPage: true });
  await page.locator('.society-switcher').click();
  await page.getByRole('button', { name: /Sai Darshan Demo CHS/ }).click();
  await expect(page.locator('.topbar-left')).toContainText('Sai Darshan Demo CHS');
  await expect(page.getByRole('link', { name: 'Residents', exact: true })).toHaveCount(0);
  await page.goto('/#/visitors');
  await expect(page.getByText('Nothing here yet')).toBeVisible();
});
test('watchman creates visitor, resident approves, watchman checks in and out', async ({
  browser,
}) => {
  const gateContext = await browser.newContext();
  const homeContext = await browser.newContext();
  const gate = await gateContext.newPage();
  const home = await homeContext.newPage();
  await login(gate, 'watchman@demo.local');
  await gate.goto('/#/visitors');
  await gate.getByRole('button', { name: 'Add visitor', exact: true }).first().click();
  const name = 'Browser Guest ' + Date.now();
  await gate.getByLabel('Mobile number', { exact: false }).fill('9000000033');
  await gate.getByLabel('Visitor name', { exact: false }).fill(name);
  await gate.getByRole('combobox', { name: 'Flat', exact: true }).selectOption({ label: 'A-101' });
  await gate.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(gate.getByRole('dialog')).toHaveCount(0);
  await login(home);
  await home.goto('/#/visitors');
  await home.getByLabel('Search records').fill(name);
  await home
    .getByRole('row')
    .filter({ hasText: name })
    .getByRole('button', { name: 'Allow', exact: true })
    .click();
  await expect(home.getByRole('row').filter({ hasText: name })).toContainText('Approved');
  await gate.getByRole('button', { name: 'Refresh records' }).click();
  await gate.getByLabel('Search records').fill(name);
  let row = gate.getByRole('row').filter({ hasText: name });
  await row.getByRole('button', { name: 'Check in', exact: true }).click();
  row = gate.getByRole('row').filter({ hasText: name });
  await row.getByRole('button', { name: 'Check out', exact: true }).click();
  await expect(row).toContainText('Checked Out');
  await gateContext.close();
  await homeContext.close();
});
test('resident can raise a complaint and add a timeline comment', async ({ page }) => {
  await login(page);
  await page.goto('/#/complaints');
  await page.getByRole('button', { name: 'Add complaint', exact: true }).first().click();
  const title = 'Browser complaint ' + Date.now();
  await page.getByLabel('Title', { exact: false }).fill(title);
  await page
    .getByLabel('Describe the issue', { exact: false })
    .fill('A browser-verified complaint for the demo society.');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByLabel('Search records').fill(title);
  await page.getByRole('button', { name: title, exact: true }).click();
  await page.getByLabel('Comment', { exact: true }).fill('Please inspect tomorrow.');
  await page.getByRole('button', { name: 'Add comment', exact: true }).click();
  await expect(page.locator('.timeline')).toContainText('Please inspect tomorrow.');
});
test('platform branding updates independently of society naming', async ({ page }) => {
  await login(page, process.env['E2E_OWNER_EMAIL'] || 'owner@e2e.test', ownerPassword);
  await page.goto('/#/branding');
  await page.getByLabel('Application name', { exact: false }).fill('My Society');
  await page.getByLabel('Short name', { exact: false }).fill('My Society');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Changes saved.')).toBeVisible();
  await page.goto('/#/societies');
  await expect(
    page.getByRole('button', { name: 'My Society Demo CHS', exact: true }),
  ).toBeVisible();
  await page.goto('/#/diagnostics');
  await expect(page.getByText('8091', { exact: true })).toBeVisible();
});
test('mobile dashboard navigation and layout', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({ path: '../.local/screenshots/mobile-dashboard.png', fullPage: true });
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.getByRole('link', { name: 'Parcels', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Parcels', exact: true })).toBeVisible();
});
test('server unavailability is presented without networking internals', async ({ page }) => {
  await page.route('**/api/v1/**', (route) => route.abort('connectionrefused'));
  await page.goto('/#/login');
  await page.getByLabel('Email or mobile number').fill('resident@demo.local');
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Unable to connect to the Society server.');
});
test('runtime API URL configuration rejects invalid protocol', async ({ page }) => {
  await page.route('**/runtime-config.json*', (route) =>
    route.fulfill({
      json: { apiBaseUrl: 'ftp://invalid.example', ngrokSkipBrowserWarning: false },
    }),
  );
  await page.goto('/#/login');
  await expect(page.getByRole('alert')).toContainText('Invalid API address.');
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeDisabled();
});
