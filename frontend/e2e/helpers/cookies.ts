import type { Cookie, Page } from '@playwright/test';
import { expect } from '@playwright/test';

const REFRESH = 'payflow_refresh';
const CSRF = 'XSRF-TOKEN';

/** Cookie attributes only — never log cookie values, JWTs, or passwords. */
function attrs(cookie: Cookie | undefined): { present: boolean; httpOnly?: boolean; secure?: boolean; sameSite?: string } {
  if (!cookie) {
    return { present: false };
  }
  return {
    present: true,
    httpOnly: cookie.httpOnly,
    secure: cookie.secure,
    sameSite: cookie.sameSite,
  };
}

export async function getNamedCookie(page: Page, name: string): Promise<Cookie | undefined> {
  const cookies = await page.context().cookies();
  return cookies.find((c) => c.name === name);
}

export async function assertSecureBrowserSessionCookies(page: Page): Promise<void> {
  const refresh = await getNamedCookie(page, REFRESH);
  const csrf = await getNamedCookie(page, CSRF);

  const refreshAttrs = attrs(refresh);
  const csrfAttrs = attrs(csrf);

  expect(refreshAttrs.present, 'payflow_refresh cookie should be present after login').toBe(true);
  expect(refreshAttrs.httpOnly, 'payflow_refresh must be HttpOnly').toBe(true);
  expect(refreshAttrs.secure, 'payflow_refresh must be Secure').toBe(true);

  expect(csrfAttrs.present, 'XSRF-TOKEN cookie should be present').toBe(true);
  expect(csrfAttrs.secure, 'XSRF-TOKEN must be Secure on HTTPS prod profile').toBe(true);
  expect(csrfAttrs.httpOnly, 'XSRF-TOKEN must be readable by the SPA (not HttpOnly)').toBe(false);
}

export async function assertRefreshCookieAbsentOrCleared(page: Page): Promise<void> {
  const refresh = await getNamedCookie(page, REFRESH);
  if (!refresh) {
    return;
  }
  // Cleared cookies may remain with empty value / expired max-age depending on browser.
  expect(refresh.value.length, 'refresh cookie must be cleared after logout').toBe(0);
}

export async function assertNoTokensInWebStorage(page: Page): Promise<void> {
  const leak = await page.evaluate(() => {
    const keys: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k) keys.push(`local:${k}`);
    }
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k) keys.push(`session:${k}`);
    }
    const suspicious = keys.filter((k) => /token|jwt|refresh|access|password|secret/i.test(k));
    return suspicious;
  });
  expect(leak, 'access/refresh tokens must not be persisted in web storage').toEqual([]);
}
