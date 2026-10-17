import type { ReactNode } from 'react';
import { Inbox } from 'lucide-react';
import { Card } from '@/components/ui/Card';

type EmptyStateProps = {
  title: string;
  description: string;
  icon?: ReactNode;
  action?: ReactNode;
};

export function EmptyState({ title, description, icon, action }: EmptyStateProps) {
  return (
    <Card className="flex flex-col items-start gap-3" data-testid="empty-state">
      <div className="rounded-pf bg-pf-primary-soft p-2 text-pf-primary" aria-hidden>
        {icon ?? <Inbox className="h-5 w-5" />}
      </div>
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-pf-ink">{title}</h2>
        <p className="max-w-xl text-sm text-pf-muted">{description}</p>
      </div>
      {action}
    </Card>
  );
}
