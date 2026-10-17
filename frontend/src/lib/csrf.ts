/**
 * Double-submit CSRF helpers for browser-session cookie endpoints.
 * Reads the non-HttpOnly XSRF-TOKEN cookie issued by Spring Security.
 */

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

export function readCsrfToken(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${CSRF_COOKIE}=`));
  if (!match) {
    return null;
  }
  return decodeURIComponent(match.slice(CSRF_COOKIE.length + 1));
}

export function csrfHeaders(): Record<string, string> {
  const token = readCsrfToken();
  return token ? { [CSRF_HEADER]: token } : {};
}

export { CSRF_COOKIE, CSRF_HEADER };
