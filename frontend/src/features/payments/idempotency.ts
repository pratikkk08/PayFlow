/** Client Idempotency-Key helpers — matches backend IdempotencyKeyValidator. */

const ALLOWED = /^[A-Za-z0-9._~:-]{8,128}$/;

export function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Fallback for rare environments without Web Crypto UUID.
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function isValidIdempotencyKey(key: string): boolean {
  return ALLOWED.test(key);
}

/**
 * Uncertain outcomes: keep the same key + payload and retry.
 * Do not mint a new key after network timeout / 5xx / in-progress.
 */
export function isUncertainMutationError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return true;
  }
  const status = 'status' in error ? Number((error as { status: unknown }).status) : NaN;
  const code = 'code' in error ? String((error as { code: unknown }).code) : '';
  if (code === 'network_error' || code === 'timeout' || code === 'idempotency_in_progress') {
    return true;
  }
  if (!Number.isFinite(status) || status === 0) {
    return true;
  }
  return status >= 500;
}
