/** UTC reporting window helpers — aligned with backend AnalyticsReportingWindow. */

export const MAX_INCLUSIVE_DAYS = 366;
export const DEFAULT_INCLUSIVE_DAYS = 30;

export type RangePreset = '7d' | '30d' | '90d' | 'custom';

export type ReportingRange = {
  from: string;
  to: string;
};

/** Today's UTC calendar date as yyyy-MM-dd. */
export function utcToday(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10);
}

export function parseUtcDate(isoDate: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match) {
    throw new Error(`Invalid UTC date: ${isoDate}`);
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  return new Date(Date.UTC(year, month - 1, day));
}

export function formatUtcDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function addUtcDays(isoDate: string, days: number): string {
  const date = parseUtcDate(isoDate);
  date.setUTCDate(date.getUTCDate() + days);
  return formatUtcDate(date);
}

export function inclusiveDayCount(from: string, to: string): number {
  const start = parseUtcDate(from).getTime();
  const end = parseUtcDate(to).getTime();
  return Math.floor((end - start) / 86_400_000) + 1;
}

export function resolvePresetRange(
  preset: Exclude<RangePreset, 'custom'>,
  today: string = utcToday(),
): ReportingRange {
  const days = preset === '7d' ? 7 : preset === '30d' ? 30 : 90;
  return {
    from: addUtcDays(today, -(days - 1)),
    to: today,
  };
}

export function validateReportingRange(from: string, to: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) {
    return 'Enter valid UTC dates (yyyy-MM-dd).';
  }
  try {
    parseUtcDate(from);
    parseUtcDate(to);
  } catch {
    return 'Enter valid UTC dates (yyyy-MM-dd).';
  }
  if (from > to) {
    return 'From must be on or before to (UTC calendar dates, inclusive).';
  }
  if (inclusiveDayCount(from, to) > MAX_INCLUSIVE_DAYS) {
    return `Reporting range must not exceed ${MAX_INCLUSIVE_DAYS} inclusive UTC days.`;
  }
  return null;
}

export function formatReportingPeriodLabel(from: string, to: string): string {
  return `${from} → ${to} (UTC)`;
}

export function hasAnalyticsActivity(overview: {
  totalPaymentCount: number;
  totalCapturedAmountMinor: number;
  totalSucceededRefundAmountMinor: number;
  riskBlockCount: number;
  riskReviewCount: number;
}): boolean {
  return (
    overview.totalPaymentCount > 0 ||
    overview.totalCapturedAmountMinor !== 0 ||
    overview.totalSucceededRefundAmountMinor !== 0 ||
    overview.riskBlockCount > 0 ||
    overview.riskReviewCount > 0
  );
}
