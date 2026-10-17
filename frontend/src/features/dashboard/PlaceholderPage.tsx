import { Construction } from 'lucide-react';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/EmptyState';
import { Badge } from '@/components/ui/Badge';

type PlaceholderPageProps = {
  title: string;
  description: string;
};

/**
 * Honest coming-soon page — no fabricated merchants, payments, or revenue.
 */
export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <div>
      <PageHeader
        title={title}
        description={description}
        actions={<Badge tone="warning">Not connected</Badge>}
      />
      <EmptyState
        icon={<Construction className="h-5 w-5" />}
        title={`${title} is not wired yet`}
        description="This screen is a layout placeholder. No live data, charts, or mock transactions are shown. Feature APIs will connect in later Phase 7 tasks."
      />
    </div>
  );
}
