import { test, expect, Page } from '@playwright/test';
import { isolated, configureRuntime } from './isolated';
test.skip(
  !isolated,
  'Use scripts/run-browser-tests.ps1; these checks must never create live dummy users.',
);
const password = process.env['E2E_PASSWORD'] || '';
const api = process.env['E2E_API_URL'] || '';
async function login(page: Page, email = 'resident@demo.local') {
  await configureRuntime(page);
  await page.goto('/#/login');
  await page.getByLabel('Email or mobile number').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).not.toHaveURL(/#\/login$/);
}
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
    true,
  );
}
test('mobile signup, pending access, approval and correct resident dashboard', async ({
  page,
  browser,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await configureRuntime(page);
  await page.goto('/#/login');
  await page.getByRole('link', { name: 'Create Account' }).click();
  await noOverflow(page);
  const stamp = Date.now(),
    email = `signup-${stamp}@e2e.test`;
  await page.getByLabel('Full name').fill('Signup Resident ' + stamp);
  await page.getByLabel('Mobile number').fill('8' + String(stamp).slice(-9));
  await page.getByLabel('Email', { exact: false }).fill(email);
  await page.getByLabel(/^Password \*/).fill(password);
  await page.getByLabel('Confirm password').fill(password);
  await page.getByRole('button', { name: 'Create Account', exact: true }).click();
  await expect(page.getByText('Account created successfully.', { exact: true })).toBeVisible();
  await login(page, email);
  await expect(page.getByRole('heading', { name: 'Your account is ready' })).toBeVisible();
  await page.getByLabel('Society code').fill('DEMO');
  await page.getByLabel('Your building and flat').fill('A-101');
  await page.getByRole('button', { name: 'Request access', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('pending approval');
  await noOverflow(page);
  const adminContext = await browser.newContext({ serviceWorkers: 'block' });
  const admin = await adminContext.newPage();
  await login(admin, 'secretary@demo.local');
  await admin.getByRole('button', { name: /My Society Demo CHS/ }).click();
  await expect(admin).toHaveURL(/dashboard/);
  await admin.goto('/#/membership-requests');
  const card = admin.locator('article').filter({ hasText: email });
  await card.getByLabel('Verified flat').selectOption({ label: 'A-101' });
  await card.getByLabel('Resident relationship').selectOption('OWNER');
  await card.getByRole('button', { name: 'Approve access' }).click();
  await expect(card).toContainText('APPROVED');
  await adminContext.close();
  await page.getByRole('button', { name: 'Refresh access' }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'My Visitors', exact: false })).toBeVisible();
  await noOverflow(page);
  await page.screenshot({
    path: '../.local/screenshots/signup-approved-mobile.png',
    fullPage: true,
  });
});
test('admin-created account is visible despite old search and membership linking refreshes without switching society', async ({
  page,
}) => {
  await login(page, process.env['E2E_OWNER_EMAIL']!);
  await page.goto('/#/platform-users');
  await page.getByLabel('Search records').fill('no-such-person');
  await page.getByRole('button', { name: 'Add user', exact: true }).first().click();
  const name = 'Visible User ' + Date.now();
  await page.getByLabel('Full name').fill(name);
  await page.getByLabel('Email', { exact: false }).fill(`visible-${Date.now()}@e2e.test`);
  await page.getByLabel('Initial password').fill(password);
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByLabel('Search records')).toHaveValue('');
  let row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toBeVisible();
  await expect(row).toContainText('No Society Assigned');
  await row.getByRole('button', { name: 'Add membership' }).click();
  await page
    .getByRole('combobox', { name: 'Society', exact: true })
    .selectOption({ label: 'My Society Demo CHS' });
  await page.getByRole('checkbox', { name: 'WATCHMAN', exact: true }).check();
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(row).toContainText('My Society Demo CHS (ACTIVE)');
  expect(await page.evaluate(() => sessionStorage.getItem('ms.managedSociety'))).toBeNull();
  await row.getByRole('button', { name: 'Edit', exact: true }).click();
  await page.getByLabel('Full name').fill(name + ' edited');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  row = page.getByRole('row').filter({ hasText: name + ' edited' });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Suspend', exact: true }).click();
  await expect(row).toContainText('Suspended');
});
test('resident mobile screens and forms fit without horizontal scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await login(page);
  for (const route of [
    'dashboard',
    'visitors',
    'parcels',
    'complaints',
    'notices',
    'meetings',
    'select-society',
  ]) {
    await page.goto('/#/' + route);
    await expect(page.locator('h1').first()).toBeVisible();
    await noOverflow(page);
  }
  for (const [route, button] of [
    ['visitors', 'Add visitor'],
    ['complaints', 'Add complaint'],
  ]) {
    await page.goto('/#/' + route);
    await page.getByRole('button', { name: button, exact: true }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await noOverflow(page);
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
  }
  await page.goto('/#/notices');
  await page.getByRole('button', { name: 'Water tank cleaning', exact: true }).click();
  await noOverflow(page);
  await page.screenshot({ path: '../.local/screenshots/mobile-notice.png', fullPage: true });
});
test('watchman has five large gate actions and phone-friendly forms', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, 'watchman@demo.local');
  for (const label of ['VISITOR', 'EXPECTED VISITOR', 'PARCEL', 'CHECK OUT', 'EMERGENCY'])
    await expect(
      page.locator('.quick-card').filter({ has: page.getByText(label, { exact: true }) }),
    ).toBeVisible();
  await page
    .locator('.quick-card')
    .filter({ has: page.getByText('PARCEL', { exact: true }) })
    .click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await noOverflow(page);
  await page.getByLabel('Find a flat').fill('A-101');
  await page.getByRole('combobox', { name: 'Flat', exact: true }).selectOption({ label: 'A-101' });
  await page.screenshot({ path: '../.local/screenshots/mobile-parcel-form.png', fullPage: true });
});
test('expired session clears access and returns to sign in', async ({ page }) => {
  await login(page);
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 401, json: { message: 'Your session has expired. Please sign in.' } }),
  );
  await page.reload();
  await expect(page).toHaveURL(/login/);
  expect(await page.evaluate(() => sessionStorage.getItem('ms.token'))).toBeNull();
});
