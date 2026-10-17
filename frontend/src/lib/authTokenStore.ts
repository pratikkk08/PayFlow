/**
 * In-memory access JWT only. Never persist to localStorage/sessionStorage/IndexedDB.
 */

let accessToken: string | null = null;
let sessionClearedListener: (() => void) | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

export function setSessionClearedListener(listener: (() => void) | null): void {
  sessionClearedListener = listener;
}

export function notifySessionCleared(): void {
  sessionClearedListener?.();
}
