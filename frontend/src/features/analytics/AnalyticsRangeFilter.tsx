import { useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import {
  type RangePreset,
  type ReportingRange,
  formatReportingPeriodLabel,
  resolvePresetRange,
  validateReportingRange,
} from '@/features/analytics/reportingRange';

type AnalyticsRangeFilterProps = {
  preset: RangePreset;
  range: ReportingRange;
  onPresetChange: (preset: Exclude<RangePreset, 'custom'>) => void;
  onEnterCustom: () => void;
  onCustomApply: (range: ReportingRange) => void;
};

const PRESETS: { id: Exclude<RangePreset, 'custom'>; label: string }[] = [
  { id: '7d', label: 'Last 7 days' },
  { id: '30d', label: 'Last 30 days' },
  { id: '90d', label: 'Last 90 days' },
];

export function AnalyticsRangeFilter({
  preset,
  range,
  onPresetChange,
  onEnterCustom,
  onCustomApply,
}: AnalyticsRangeFilterProps) {
  const groupId = useId();
  const [draftFrom, setDraftFrom] = useState(range.from);
  const [draftTo, setDraftTo] = useState(range.to);
  const [customError, setCustomError] = useState<string | null>(null);

  const showCustom = preset === 'custom';

  return (
    <div className="space-y-3" data-testid="analytics-range-filter">
      <div
        className="flex flex-wrap gap-2"
        role="group"
        aria-labelledby={`${groupId}-label`}
      >
        <span id={`${groupId}-label`} className="sr-only">
          Reporting period
        </span>
        {PRESETS.map((item) => (
          <Button
            key={item.id}
            size="sm"
            variant={preset === item.id ? 'primary' : 'secondary'}
            aria-pressed={preset === item.id}
            onClick={() => {
              setCustomError(null);
              const next = resolvePresetRange(item.id);
              setDraftFrom(next.from);
              setDraftTo(next.to);
              onPresetChange(item.id);
            }}
          >
            {item.label}
          </Button>
        ))}
        <Button
          size="sm"
          variant={preset === 'custom' ? 'primary' : 'secondary'}
          aria-pressed={preset === 'custom'}
          onClick={() => {
            setCustomError(null);
            setDraftFrom(range.from);
            setDraftTo(range.to);
            onEnterCustom();
          }}
        >
          Custom range
        </Button>
      </div>

      {showCustom ? (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <Input
            label="From (UTC)"
            type="date"
            value={draftFrom}
            onChange={(event) => setDraftFrom(event.target.value)}
            error={customError ?? undefined}
          />
          <Input
            label="To (UTC)"
            type="date"
            value={draftTo}
            onChange={(event) => setDraftTo(event.target.value)}
          />
          <Button
            variant="secondary"
            onClick={() => {
              const validation = validateReportingRange(draftFrom, draftTo);
              if (validation) {
                setCustomError(validation);
                return;
              }
              setCustomError(null);
              onCustomApply({ from: draftFrom, to: draftTo });
            }}
          >
            Apply
          </Button>
        </div>
      ) : null}

      <p className="text-xs text-pf-muted" data-testid="reporting-period-label">
        Reporting period: {formatReportingPeriodLabel(range.from, range.to)}
      </p>
    </div>
  );
}
