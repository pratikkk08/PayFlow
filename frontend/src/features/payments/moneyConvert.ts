/**
 * Convert INR major-unit input (rupees) to integer minor units (paise)
 * without IEEE floating-point rounding errors.
 */

export type MinorParseResult =
  | { ok: true; amountMinor: number }
  | { ok: false; error: string };

/** Max practical amount for UI validation (backend only requires @Positive). */
export const MAX_AMOUNT_MINOR = 100_000_00; // ₹100,000.00

export function parseInrMajorToMinor(raw: string): MinorParseResult {
  const trimmed = raw.trim().replace(/,/g, '');
  if (!trimmed) {
    return { ok: false, error: 'Enter an amount in INR.' };
  }
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) {
    return {
      ok: false,
      error: 'Use a positive INR amount with up to 2 decimal places (e.g. 1250.50).',
    };
  }

  const [wholePart, fractionPart = ''] = trimmed.split('.');
  const whole = wholePart.replace(/^0+(?=\d)/, '') || '0';
  const paise = `${fractionPart}00`.slice(0, 2);

  // Decimal string math — avoid Number(rupees) * 100.
  const amountMinor = Number(whole) * 100 + Number(paise);
  if (!Number.isSafeInteger(amountMinor)) {
    return { ok: false, error: 'Amount is too large.' };
  }
  if (amountMinor < 1) {
    return { ok: false, error: 'Amount must be at least ₹0.01.' };
  }
  if (amountMinor > MAX_AMOUNT_MINOR) {
    return { ok: false, error: 'Amount exceeds the sandbox UI limit of ₹100,000.00.' };
  }
  return { ok: true, amountMinor };
}

export function formatInrMajorInputFromMinor(amountMinor: number): string {
  const whole = Math.trunc(amountMinor / 100);
  const paise = Math.abs(amountMinor % 100)
    .toString()
    .padStart(2, '0');
  return `${whole}.${paise}`;
}
