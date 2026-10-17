import { formatUtcChartDate } from '@/features/analytics/money';
import type { AnalyticsDailyPoint } from '@/types/api';

export type ChartMetric =
  | 'capturedAmountMinor'
  | 'paymentsCreated'
  | 'succeededRefundAmountMinor';

export type ChartRow = {
  date: string;
  label: string;
  value: number;
};

export function mapDailyChartRows(
  days: AnalyticsDailyPoint[],
  metric: ChartMetric,
): ChartRow[] {
  return days.map((day) => ({
    date: day.date,
    label: formatUtcChartDate(day.date),
    value: day[metric],
  }));
}

export function isMoneyMetric(metric: ChartMetric): boolean {
  return metric === 'capturedAmountMinor' || metric === 'succeededRefundAmountMinor';
}
