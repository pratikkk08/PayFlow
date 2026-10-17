import { vi } from 'vitest';
import type { AnalyticsDailyResponse, AnalyticsOverviewResponse } from '@/types/api';
import { mockMerchants } from '@/test/merchantMocks';

export const DISCLAIMER =
  'Sandbox activity metrics only — not settled bank balances, payouts, or real revenue. ' +
  'Status counts reflect current payment status; historical transitions are not reconstructed.';

export function buildEmptyOverview(
  merchantId: string,
  from: string,
  to: string,
): AnalyticsOverviewResponse {
  return {
    merchantId,
    reportingTimezone: 'UTC',
    fromInclusive: from,
    toInclusive: to,
    rangeStartInclusive: `${from}T00:00:00Z`,
    rangeEndExclusive: `${to}T00:00:00Z`,
    currency: 'INR',
    totalPaymentCount: 0,
    createdCount: 0,
    authorizedCount: 0,
    capturedCount: 0,
    failedCount: 0,
    cancelledCount: 0,
    totalCapturedAmountMinor: 0,
    totalSucceededRefundAmountMinor: 0,
    netCapturedAmountMinor: 0,
    paymentCaptureRate: null,
    paymentCaptureRateDefinition:
      'capturedCount / totalPaymentCount for payments with created_at in the reporting window ' +
      '(current CAPTURED status); null when totalPaymentCount is 0',
    riskBlockCount: 0,
    riskReviewCount: 0,
    disclaimer: DISCLAIMER,
  };
}

export function buildSampleOverview(
  merchantId: string,
  from: string,
  to: string,
): AnalyticsOverviewResponse {
  return {
    ...buildEmptyOverview(merchantId, from, to),
    totalPaymentCount: 10,
    createdCount: 1,
    authorizedCount: 1,
    capturedCount: 6,
    failedCount: 2,
    cancelledCount: 0,
    totalCapturedAmountMinor: 125_050,
    totalSucceededRefundAmountMinor: 5_000,
    netCapturedAmountMinor: 120_050,
    paymentCaptureRate: 0.6,
    riskBlockCount: 3,
    riskReviewCount: 1,
  };
}

export function buildEmptyDaily(
  merchantId: string,
  from: string,
  to: string,
): AnalyticsDailyResponse {
  return {
    merchantId,
    reportingTimezone: 'UTC',
    fromInclusive: from,
    toInclusive: to,
    rangeStartInclusive: `${from}T00:00:00Z`,
    rangeEndExclusive: `${to}T00:00:00Z`,
    currency: 'INR',
    days: [
      {
        date: from,
        paymentsCreated: 0,
        paymentsCaptured: 0,
        paymentsFailed: 0,
        capturedAmountMinor: 0,
        succeededRefundAmountMinor: 0,
        netAmountMinor: 0,
      },
      {
        date: to,
        paymentsCreated: 0,
        paymentsCaptured: 0,
        paymentsFailed: 0,
        capturedAmountMinor: 0,
        succeededRefundAmountMinor: 0,
        netAmountMinor: 0,
      },
    ],
    disclaimer: DISCLAIMER,
  };
}

export function buildSampleDaily(
  merchantId: string,
  from: string,
  to: string,
): AnalyticsDailyResponse {
  return {
    ...buildEmptyDaily(merchantId, from, to),
    days: [
      {
        date: from,
        paymentsCreated: 4,
        paymentsCaptured: 3,
        paymentsFailed: 1,
        capturedAmountMinor: 75_000,
        succeededRefundAmountMinor: 0,
        netAmountMinor: 75_000,
      },
      {
        date: to,
        paymentsCreated: 6,
        paymentsCaptured: 3,
        paymentsFailed: 1,
        capturedAmountMinor: 50_050,
        succeededRefundAmountMinor: 5_000,
        netAmountMinor: 45_050,
      },
    ],
  };
}

export const analyticsApiMocks = {
  fetchAnalyticsOverview: vi.fn(),
  fetchAnalyticsDaily: vi.fn(),
};

export function resetAnalyticsApiMocks(options?: {
  mode?: 'sample' | 'empty';
  overview?: AnalyticsOverviewResponse;
  daily?: AnalyticsDailyResponse;
}) {
  const mode = options?.mode ?? 'sample';
  analyticsApiMocks.fetchAnalyticsOverview.mockReset();
  analyticsApiMocks.fetchAnalyticsDaily.mockReset();

  analyticsApiMocks.fetchAnalyticsOverview.mockImplementation(
    async (merchantId: string, range: { from: string; to: string }) => {
      if (options?.overview) {
        return { ...options.overview, merchantId, fromInclusive: range.from, toInclusive: range.to };
      }
      return mode === 'empty'
        ? buildEmptyOverview(merchantId, range.from, range.to)
        : buildSampleOverview(merchantId, range.from, range.to);
    },
  );

  analyticsApiMocks.fetchAnalyticsDaily.mockImplementation(
    async (merchantId: string, range: { from: string; to: string }) => {
      if (options?.daily) {
        return { ...options.daily, merchantId, fromInclusive: range.from, toInclusive: range.to };
      }
      return mode === 'empty'
        ? buildEmptyDaily(merchantId, range.from, range.to)
        : buildSampleDaily(merchantId, range.from, range.to);
    },
  );
}

/** Default merchant id used by selection when mocks load. */
export const defaultAnalyticsMerchantId = mockMerchants[0].id;
