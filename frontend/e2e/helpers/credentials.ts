import { randomBytes } from 'node:crypto';

export type E2eUser = {
  fullName: string;
  email: string;
  /** Ephemeral password for this run only — never log or assert the raw value. */
  password: string;
};

/**
 * Unique sandbox identity per test run. Password meets RegisterRequest rules
 * (10–128 chars, upper, lower, digit) without embedding long-lived secrets.
 */
export function uniqueTestUser(label: string): E2eUser {
  const stamp = `${Date.now().toString(36)}-${randomBytes(4).toString('hex')}`;
  const password = `E2e${stamp}aA1!`;
  return {
    fullName: `E2E ${label} ${stamp}`,
    email: `e2e.${label}.${stamp}@example.com`.toLowerCase(),
    password: password.slice(0, 48),
  };
}

export function uniqueMerchantName(label: string): string {
  const stamp = `${Date.now().toString(36)}-${randomBytes(3).toString('hex')}`;
  return `E2E ${label} ${stamp}`;
}

export function uniqueReference(prefix: string): string {
  const stamp = `${Date.now().toString(36)}${randomBytes(2).toString('hex')}`;
  return `${prefix}_${stamp}`.slice(0, 64);
}
