/** Format INR amounts stored as integer minor units (paise). */

export function formatInrMinor(amountMinor: number): string {
  const major = amountMinor / 100;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(major);
}

/** Compact axis labels for charts (still INR major units). */
export function formatInrMinorCompact(amountMinor: number): string {
  const major = amountMinor / 100;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(major);
}

/**
 * paymentCaptureRate from backend: capturedCount / totalPaymentCount.
 * Null when totalPaymentCount is 0.
 */
export function formatCaptureRate(rate: number | string | null | undefined): string {
  if (rate === null || rate === undefined) {
    return '—';
  }
  const value = typeof rate === 'string' ? Number(rate) : rate;
  if (!Number.isFinite(value)) {
    return '—';
  }
  return `${(value * 100).toFixed(1)}%`;
}

export function formatUtcChartDate(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match) {
    return isoDate;
  }
  const month = Number(match[2]);
  const day = Number(match[3]);
  const months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];
  return `${months[month - 1]} ${day}`;
}
