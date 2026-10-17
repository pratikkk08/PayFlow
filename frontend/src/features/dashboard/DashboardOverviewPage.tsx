import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BarChart3, Building2, Plus } from 'lucide-react';
import { AnalyticsRangeFilter } from '@/features/analytics/AnalyticsRangeFilter';
import { DailyAnalyticsChart } from '@/features/analytics/DailyAnalyticsChart';
import { MetricCards } from '@/features/analytics/MetricCards';
import {
  type RangePreset,
  type ReportingRange,
  hasAnalyticsActivity,
  resolvePresetRange,
} from '@/features/analytics/reportingRange';
import { useMerchantAnalytics } from '@/features/analytics/useMerchantAnalytics';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { MerchantStatusBadge } from '@/features/merchants/MerchantStatusBadge';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { PageHeader } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeleton';

/**
 * Merchant analytics dashboard — real overview + daily series from PayFlow APIs.
 */
export function DashboardOverviewPage() {
  const {
    merchants,
    selectedMerchant,
    selectedMerchantId,
    isLoading: merchantsLoading,
    isError: merchantsError,
    errorMessage: merchantsErrorMessage,
    refetch: refetchMerchants,
  } = useMerchantSelection();

  const [preset, setPreset] = useState<RangePreset>('30d');
  const [range, setRange] = useState<ReportingRange>(() => resolvePresetRange('30d'));

  const analytics = useMerchantAnalytics(selectedMerchantId, range);

  if (merchantsLoading) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Loading dashboard">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (merchantsError) {
    return (
      <ErrorState
        title="Unable to load merchants"
        description={merchantsErrorMessage ?? 'Request failed.'}
        action={
          <Button variant="secondary" onClick={() => refetchMerchants()}>
            Retry
          </Button>
        }
      />
    );
  }

  if (merchants.length === 0) {
    return (
      <div>
        <PageHeader
          title="Overview"
          description="Get started by creating a sandbox merchant account."
        />
        <EmptyState
          title="Create your first merchant"
          description="Merchant workspaces scope payments, API keys, webhooks, and analytics. No live funds or card data."
          icon={<Building2 className="h-5 w-5" />}
          action={
            <Link to="/merchants/new">
              <Button>
                <Plus className="h-4 w-4" aria-hidden />
                Create merchant
              </Button>
            </Link>
          }
        />
      </div>
    );
  }

  if (!selectedMerchant || !selectedMerchantId) {
    return (
      <div>
        <PageHeader title="Overview" description="Choose a merchant workspace to view analytics." />
        <EmptyState
          title="Select a merchant"
          description="Choose a merchant from the top bar to continue."
          action={
            <Link to="/merchants">
              <Button variant="secondary">Open merchants</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const showAnalyticsSkeleton = analytics.isLoading || (analytics.isFetching && !analytics.overview);
  const activity =
    analytics.overview != null ? hasAnalyticsActivity(analytics.overview) : false;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Overview"
        description="Simulated payment activity for the selected merchant. Not bank-settled revenue or live funds."
        actions={
          <Link to="/merchants">
            <Button variant="secondary">Manage merchants</Button>
          </Link>
        }
      />

      <Card className="space-y-3" data-testid="selected-merchant-summary">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-lg font-semibold text-pf-ink">{selectedMerchant.name}</h2>
          <MerchantStatusBadge status={selectedMerchant.status} />
          <Badge tone="info">Sandbox metrics</Badge>
        </div>
        <p className="text-sm text-pf-muted">
          {selectedMerchant.description?.trim() ||
            'No description. Use the merchant selector to switch workspaces.'}
        </p>
      </Card>

      <AnalyticsRangeFilter
        preset={preset}
        range={range}
        onPresetChange={(next) => {
          setPreset(next);
          setRange(resolvePresetRange(next));
        }}
        onEnterCustom={() => setPreset('custom')}
        onCustomApply={(next) => {
          setPreset('custom');
          setRange(next);
        }}
      />

      {analytics.accessRevoked ? (
        <ErrorState
          title="Merchant analytics unavailable"
          description={
            analytics.errorMessage ??
            'This merchant may have been deleted or you no longer have access.'
          }
          action={
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => analytics.refetch()}>
                Retry
              </Button>
              <Button variant="secondary" onClick={() => refetchMerchants()}>
                Refresh merchants
              </Button>
            </div>
          }
        />
      ) : null}

      {!analytics.accessRevoked && analytics.isError ? (
        <ErrorState
          title="Unable to load analytics"
          description={analytics.errorMessage ?? 'Request failed.'}
          action={
            <Button variant="secondary" onClick={() => analytics.refetch()}>
              Retry
            </Button>
          }
        />
      ) : null}

      {!analytics.accessRevoked && !analytics.isError && showAnalyticsSkeleton ? (
        <div className="space-y-4" aria-busy="true" aria-label="Loading analytics">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 8 }).map((_, index) => (
              <Skeleton key={index} className="h-24 w-full" />
            ))}
          </div>
          <Skeleton className="h-64 w-full" />
        </div>
      ) : null}

      {!analytics.accessRevoked &&
      !analytics.isError &&
      !showAnalyticsSkeleton &&
      analytics.overview ? (
        <>
          <p className="text-xs text-pf-muted" data-testid="analytics-disclaimer">
            {analytics.overview.disclaimer}
          </p>

          <MetricCards overview={analytics.overview} />

          {!activity ? (
            <EmptyState
              title="No simulated activity yet"
              description="Create sandbox payments for this merchant to populate overview metrics and daily charts. Empty zeros are not invented — the API returned no activity in this UTC window."
              icon={<BarChart3 className="h-5 w-5" />}
            />
          ) : (
            <div className="grid gap-4 lg:grid-cols-1 xl:grid-cols-1" data-testid="analytics-charts">
              <DailyAnalyticsChart
                title="Daily captured amount"
                description="INR captured by captured_at (CAPTURED only)."
                days={analytics.daily?.days ?? []}
                metric="capturedAmountMinor"
                empty={!activity}
                testId="chart-captured-amount"
              />
              <DailyAnalyticsChart
                title="Daily payment volume"
                description="Payments created per UTC calendar day."
                days={analytics.daily?.days ?? []}
                metric="paymentsCreated"
                empty={!activity}
                testId="chart-payment-volume"
              />
              <DailyAnalyticsChart
                title="Daily refund amount"
                description="Succeeded refund amounts by completed_at."
                days={analytics.daily?.days ?? []}
                metric="succeededRefundAmountMinor"
                empty={!activity}
                testId="chart-refund-amount"
              />
            </div>
          )}
        </>
      ) : null}
    </div>
  );
}
