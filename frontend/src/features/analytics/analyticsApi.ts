import { apiClient } from '@/lib/apiClient';
import type { AnalyticsDailyResponse, AnalyticsOverviewResponse } from '@/types/api';

export type AnalyticsRangeParams = {
  from: string;
  to: string;
};

export async function fetchAnalyticsOverview(
  merchantId: string,
  range: AnalyticsRangeParams,
): Promise<AnalyticsOverviewResponse> {
  const { data } = await apiClient.get<AnalyticsOverviewResponse>(
    `/api/v1/merchants/${merchantId}/analytics/overview`,
    { params: { from: range.from, to: range.to } },
  );
  return data;
}

export async function fetchAnalyticsDaily(
  merchantId: string,
  range: AnalyticsRangeParams,
): Promise<AnalyticsDailyResponse> {
  const { data } = await apiClient.get<AnalyticsDailyResponse>(
    `/api/v1/merchants/${merchantId}/analytics/daily`,
    { params: { from: range.from, to: range.to } },
  );
  return data;
}
