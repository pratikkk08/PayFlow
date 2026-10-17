/**
 * Persists selected merchant ID as a UX preference (not a credential).
 * Scoped per user so account switches cannot reuse another user's selection.
 */

const PREFIX = 'payflow.selectedMerchantId.';

export function merchantSelectionStorageKey(userId: string): string {
  return `${PREFIX}${userId}`;
}

export function readStoredMerchantId(userId: string): string | null {
  try {
    const value = localStorage.getItem(merchantSelectionStorageKey(userId));
    return value && value.trim() ? value.trim() : null;
  } catch {
    return null;
  }
}

export function writeStoredMerchantId(userId: string, merchantId: string): void {
  try {
    localStorage.setItem(merchantSelectionStorageKey(userId), merchantId);
  } catch {
    // Ignore quota / private-mode failures — selection still works in memory.
  }
}

export function clearStoredMerchantId(userId: string): void {
  try {
    localStorage.removeItem(merchantSelectionStorageKey(userId));
  } catch {
    // ignore
  }
}

/** Resolve a valid selection from owned merchants; prefers ACTIVE when auto-picking. */
export function resolveMerchantSelection(
  merchants: { id: string; status: string }[],
  preferredId: string | null,
): string | null {
  if (merchants.length === 0) {
    return null;
  }
  if (preferredId && merchants.some((m) => m.id === preferredId)) {
    return preferredId;
  }
  const active = merchants.find((m) => m.status === 'ACTIVE');
  return (active ?? merchants[0]).id;
}
