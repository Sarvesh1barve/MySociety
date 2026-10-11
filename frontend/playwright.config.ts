import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  timeout: 45000,
  expect: { timeout: 15000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] || 'http://localhost:4200',
    headless: true,
    // Runtime overrides must reach the isolated API on every reload, never the live API.
    serviceWorkers: process.env['E2E_ISOLATED'] === 'true' ? 'block' : 'allow',
    viewport: { width: 1440, height: 1050 },
    trace: 'off',
    screenshot: 'only-on-failure',
  },
});
