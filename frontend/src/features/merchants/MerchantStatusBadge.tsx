import { Badge } from '@/components/ui/Badge';
import type { MerchantStatus } from '@/types/api';

const toneByStatus: Record<MerchantStatus, 'success' | 'warning' | 'neutral'> = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  CLOSED: 'neutral',
};

export function MerchantStatusBadge({ status }: { status: MerchantStatus }) {
  return <Badge tone={toneByStatus[status]}>{status}</Badge>;
}
