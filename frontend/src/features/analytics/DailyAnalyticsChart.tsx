import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import {
  type ChartMetric,
  type ChartRow,
  isMoneyMetric,
  mapDailyChartRows,
} from '@/features/analytics/chartData';
import { formatInrMinor, formatInrMinorCompact } from '@/features/analytics/money';
import type { AnalyticsDailyPoint } from '@/types/api';

type DailyAnalyticsChartProps = {
  title: string;
  description: string;
  days: AnalyticsDailyPoint[];
  metric: ChartMetric;
  empty: boolean;
  testId: string;
};

export function DailyAnalyticsChart({
  title,
  description,
  days,
  metric,
  empty,
  testId,
}: DailyAnalyticsChartProps) {
  const rows = mapDailyChartRows(days, metric);
  const money = isMoneyMetric(metric);
  const summary = empty
    ? `${title}: no simulated activity in this period.`
    : `${title}: ${rows.length} UTC days.`;

  return (
    <Card className="space-y-3" data-testid={testId} as="section" aria-label={title}>
      <div className="space-y-1">
        <h3 className="text-sm font-semibold text-pf-ink">{title}</h3>
        <p className="text-xs text-pf-muted">{description}</p>
        <p className="sr-only">{summary}</p>
      </div>

      {empty ? (
        <EmptyState
          title="No chart data"
          description="No simulated payments or refunds in this reporting window."
        />
      ) : money ? (
        <div className="h-56 w-full" role="img" aria-label={summary}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 11, fill: '#64748b' }}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fontSize: 11, fill: '#64748b' }}
                tickFormatter={(value: number) => formatInrMinorCompact(value)}
                width={64}
              />
              <Tooltip
                formatter={(value) => [
                  formatInrMinor(typeof value === 'number' ? value : Number(value)),
                  title,
                ]}
                labelFormatter={(_, payload) => {
                  const row = payload?.[0]?.payload as ChartRow | undefined;
                  return row ? `${row.date} (UTC)` : '';
                }}
              />
              <Area
                type="monotone"
                dataKey="value"
                stroke="#2563eb"
                fill="#eff6ff"
                strokeWidth={2}
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="h-56 w-full" role="img" aria-label={summary}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 11, fill: '#64748b' }}
                interval="preserveStartEnd"
              />
              <YAxis
                allowDecimals={false}
                tick={{ fontSize: 11, fill: '#64748b' }}
                width={40}
              />
              <Tooltip
                formatter={(value) => [
                  typeof value === 'number' ? value.toLocaleString('en-IN') : String(value),
                  title,
                ]}
                labelFormatter={(_, payload) => {
                  const row = payload?.[0]?.payload as ChartRow | undefined;
                  return row ? `${row.date} (UTC)` : '';
                }}
              />
              <Bar dataKey="value" fill="#2563eb" radius={[4, 4, 0, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  );
}
