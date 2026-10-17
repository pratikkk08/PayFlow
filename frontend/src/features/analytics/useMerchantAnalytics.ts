import { useQuery } from '@tanstack/react-query';
import { fetchAnalyticsDaily, fetchAnalyticsOverview } from '@/features/analytics/analyticsApi';
import { analyticsKeys } from '@/features/analytics/analyticsKeys';
import type { ReportingRange } from '@/features/analytics/reportingRange';
import { ApiClientError } from '@/lib/apiClient';

export function useMerchantAnalytics(merchantId: string | null, range: ReportingRange) {
  const enabled = Boolean(merchantId);

  const overviewQuery = useQuery({
    queryKey: analyticsKeys.overview(merchantId ?? 'none', range.from, range.to),
    queryFn: () => fetchAnalyticsOverview(merchantId!, range),
    enabled,
  });

  const dailyQuery = useQuery({
    queryKey: analyticsKeys.daily(merchantId ?? 'none', range.from, range.to),
    queryFn: () => fetchAnalyticsDaily(merchantId!, range),
    enabled,
  });

  const isLoading = enabled && (overviewQuery.isLoading || dailyQuery.isLoading);
  const isFetching = overviewQuery.isFetching || dailyQuery.isFetching;
  const isError = overviewQuery.isError || dailyQuery.isError;

  const error =
    overviewQuery.error instanceof Error
      ? overviewQuery.error
      : dailyQuery.error instanceof Error
        ? dailyQuery.error
        : null;

  const accessRevoked =
    error instanceof ApiClientError && (error.status === 403 || error.status === 404);

  return {
    overview: overviewQuery.data,
    daily: dailyQuery.data,
    isLoading,
    isFetching,
    isError,
    errorMessage: error?.message ?? null,
    accessRevoked,
    refetch: () => {
      void overviewQuery.refetch();
      void dailyQuery.refetch();
    },
  };
}
