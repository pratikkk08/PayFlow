import type { ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Card } from '@/components/ui/Card';

type ErrorStateProps = {
  title?: string;
  description: string;
  action?: ReactNode;
};

export function ErrorState({
  title = 'Something went wrong',
  description,
  action,
}: ErrorStateProps) {
  return (
    <Card
      className="flex flex-col items-start gap-3 border-red-100 bg-pf-danger-soft"
      role="alert"
      data-testid="error-state"
    >
      <div className="rounded-pf bg-white p-2 text-pf-danger" aria-hidden>
        <AlertTriangle className="h-5 w-5" />
      </div>
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-pf-ink">{title}</h2>
        <p className="max-w-xl text-sm text-pf-muted">{description}</p>
      </div>
      {action}
    </Card>
  );
}
