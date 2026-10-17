import { Badge } from '@/components/ui/Badge';
import type { RefundStatus } from '@/types/api';

const toneByStatus: Record<RefundStatus, 'neutral' | 'info' | 'success' | 'warning' | 'danger'> = {
  PENDING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
};

export function RefundStatusBadge({ status }: { status: RefundStatus }) {
  return <Badge tone={toneByStatus[status]}>{status}</Badge>;
}
