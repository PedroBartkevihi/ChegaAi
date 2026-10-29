import { defineConfig, devices } from '@playwright/test';
import { frontendURL, apiURL, databaseURL } from './tests/environment';

databaseURL();
const mode = process.env.E2E_MODE || 'dev';
if (!['dev', 'static'].includes(mode)) throw new Error('E2E_MODE deve ser dev ou static.');
if (mode === 'static' && frontendURL.origin === apiURL.origin) throw new Error('O teste estático exige origens distintas para exercitar CORS.');
const port = frontendURL.port || (frontendURL.protocol === 'https:' ? '443' : '80');
const command = mode === 'static'
  ? 'node tests/serve-static.mjs'
  : `npm run dev -- --hostname ${frontendURL.hostname} --port ${port}`;

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'test-results/report.json' }]],
  use: { baseURL: frontendURL.origin, trace: 'retain-on-failure', screenshot: 'only-on-failure', video: 'retain-on-failure' },
  webServer: {
    command,
    url: frontendURL.origin,
    reuseExistingServer: !process.env.CI && mode === 'dev',
    env: { E2E_BASE_URL: frontendURL.origin, API_URL: apiURL.origin },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
