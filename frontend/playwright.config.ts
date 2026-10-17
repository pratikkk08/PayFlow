import { defineConfig, devices } from '@playwright/test';

/**
 * Browser E2E:
 * - Default: local Vite + Spring Boot (HTTP). Secure cookies are not proven here.
 * - HTTPS deploy: set PLAYWRIGHT_BASE_URL=https://127.0.0.1:18443 and
 *   PLAYWRIGHT_SKIP_WEBSERVER=1 (see docs/SETUP.md Task 9.2).
 *
 * Env:
 * - PLAYWRIGHT_BASE_URL — frontend origin (default http://127.0.0.1:5173)
 * - PLAYWRIGHT_SKIP_WEBSERVER — set to "1" when the frontend is already running
 */
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173';
const isHttps = baseURL.startsWith('https:');
const skipWebServer = process.env.PLAYWRIGHT_SKIP_WEBSERVER === '1' || isHttps;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: isHttps ? 90_000 : 60_000,
  expect: {
    timeout: 15_000,
  },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    // Local self-signed deploy only — does not prove OS trust-store validation.
    ignoreHTTPSErrors: isHttps,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 15_000,
    navigationTimeout: 45_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: skipWebServer
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 5173',
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
