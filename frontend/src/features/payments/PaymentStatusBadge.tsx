import { Badge } from '@/components/ui/Badge';
import type { PaymentStatus } from '@/types/api';

const toneByStatus: Record<PaymentStatus, 'neutral' | 'info' | 'success' | 'warning' | 'danger'> = {
  CREATED: 'neutral',
  AUTHORIZED: 'info',
  CAPTURED: 'success',
  FAILED: 'danger',
  CANCELLED: 'warning',
};

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge tone={toneByStatus[status]}>{status}</Badge>;
}
