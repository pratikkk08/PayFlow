import { Card } from '@/components/ui/Card';
import { formatCaptureRate, formatInrMinor } from '@/features/analytics/money';
import type { AnalyticsOverviewResponse } from '@/types/api';

type MetricCardsProps = {
  overview: AnalyticsOverviewResponse;
};

type MetricDef = {
  id: string;
  label: string;
  value: string;
  hint?: string;
};

export function MetricCards({ overview }: MetricCardsProps) {
  const metrics: MetricDef[] = [
    {
      id: 'total-payments',
      label: 'Total payments',
      value: overview.totalPaymentCount.toLocaleString('en-IN'),
      hint: 'Created in window (current status)',
    },
    {
      id: 'captured-amount',
      label: 'Captured amount',
      value: formatInrMinor(overview.totalCapturedAmountMinor),
      hint: 'CAPTURED by captured_at',
    },
    {
      id: 'refund-amount',
      label: 'Successful refunds',
      value: formatInrMinor(overview.totalSucceededRefundAmountMinor),
      hint: 'SUCCEEDED by completed_at',
    },
    {
      id: 'net-captured',
      label: 'Net captured',
      value: formatInrMinor(overview.netCapturedAmountMinor),
      hint: 'Captured − successful refunds',
    },
    {
      id: 'capture-rate',
      label: 'Capture rate',
      value: formatCaptureRate(overview.paymentCaptureRate),
      hint: overview.paymentCaptureRateDefinition,
    },
    {
      id: 'failed-payments',
      label: 'Failed payments',
      value: overview.failedCount.toLocaleString('en-IN'),
    },
    {
      id: 'risk-block',
      label: 'Risk BLOCK',
      value: overview.riskBlockCount.toLocaleString('en-IN'),
    },
    {
      id: 'risk-review',
      label: 'Risk REVIEW',
      value: overview.riskReviewCount.toLocaleString('en-IN'),
    },
  ];

  return (
    <div
      className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
      data-testid="analytics-metric-cards"
    >
      {metrics.map((metric) => (
        <Card key={metric.id} className="space-y-1" data-testid={`metric-${metric.id}`}>
          <p className="text-xs font-medium tracking-wide text-pf-muted uppercase">
            {metric.label}
          </p>
          <p className="text-xl font-semibold text-pf-ink tabular-nums">{metric.value}</p>
          {metric.hint ? <p className="text-xs text-pf-muted">{metric.hint}</p> : null}
        </Card>
      ))}
    </div>
  );
}
