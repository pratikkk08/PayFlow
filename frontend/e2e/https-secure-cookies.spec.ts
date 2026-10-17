import { test, expect } from '@playwright/test';
import { loginUser, logout, registerUser } from './helpers/auth';
import {
  assertNoTokensInWebStorage,
  assertRefreshCookieAbsentOrCleared,
  assertSecureBrowserSessionCookies,
} from './helpers/cookies';
import { uniqueTestUser } from './helpers/credentials';

/**
 * Production-like Secure-cookie verification against local HTTPS deploy Compose.
 *
 * Requires:
 *   PLAYWRIGHT_BASE_URL=https://127.0.0.1:18443
 *   PLAYWRIGHT_SKIP_WEBSERVER=1
 *
 * Self-signed certs: playwright.config enables ignoreHTTPSErrors only when baseURL is https.
 * That does NOT prove OS-trusted certificate validation — only Secure-cookie + session behavior.
 */
const httpsReady = (process.env.PLAYWRIGHT_BASE_URL ?? '').startsWith('https:');

test.describe('HTTPS Secure-cookie browser auth', () => {
  test.skip(!httpsReady, 'Set PLAYWRIGHT_BASE_URL to the HTTPS deploy origin (e.g. https://127.0.0.1:18443)');

  test('register, Secure cookies, session restore, logout invalidation', async ({ page }) => {
    const user = uniqueTestUser('https');

    await registerUser(page, user);
    await loginUser(page, user);

    await expect(page.getByRole('heading', { name: /^overview$/i })).toBeVisible();
    await assertSecureBrowserSessionCookies(page);
    await assertNoTokensInWebStorage(page);

    await page.reload();
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByRole('heading', { name: /^overview$/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /account menu/i })).toBeVisible();
    await assertSecureBrowserSessionCookies(page);

    await logout(page);
    await assertRefreshCookieAbsentOrCleared(page);

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /^sign in$/i })).toBeVisible();

    // Reloading login must not restore a session from a cleared refresh cookie.
    await page.reload();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });
});
