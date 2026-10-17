import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { formatInrMinor } from '@/features/analytics/money';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { formatTimestamp } from '@/features/payments/formatTimestamp';
import { listPayments } from '@/features/payments/paymentApi';
import { paymentKeys } from '@/features/payments/paymentKeys';
import { PaymentStatusBadge } from '@/features/payments/PaymentStatusBadge';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { PageHeader } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeleton';

/**
 * Merchant-scoped refunds entry point.
 * There is no global merchant refund listing API — navigate via CAPTURED payments.
 */
export function RefundsPage() {
  const navigate = useNavigate();
  const {
    selectedMerchant,
    selectedMerchantId,
    merchants,
    isLoading: merchantsLoading,
    isError: merchantsError,
    errorMessage: merchantsErrorMessage,
    refetch: refetchMerchants,
  } = useMerchantSelection();

  const [page, setPage] = useState(0);
  const size = 20;

  const listParams = useMemo(
    () => ({ status: 'CAPTURED' as const, page, size }),
    [page],
  );

  const paymentsQuery = useQuery({
    queryKey: paymentKeys.list(selectedMerchantId ?? 'none', listParams),
    queryFn: () =>
      listPayments({
        merchantId: selectedMerchantId!,
        status: 'CAPTURED',
        page,
        size,
      }),
    enabled: Boolean(selectedMerchantId),
  });

  if (merchantsLoading) {
    return (
      <div className="space-y-4" aria-busy="true">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (merchantsError) {
    return (
      <ErrorState
        title="Unable to load merchants"
        description={merchantsErrorMessage ?? 'Request failed.'}
        action={
          <Button variant="secondary" onClick={() => refetchMerchants()}>
            Retry
          </Button>
        }
      />
    );
  }

  if (!selectedMerchantId || !selectedMerchant || merchants.length === 0) {
    return (
      <div>
        <PageHeader title="Refunds" description="Simulated refunds for captured payments." />
        <EmptyState
          title="Select a merchant"
          description="Refunds are scoped per merchant via captured payments."
          action={
            <Link to="/merchants">
              <Button variant="secondary">Open merchants</Button>
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Refunds"
        description={`SIMULATED refunds for ${selectedMerchant.name}.`}
        actions={<Badge tone="info">Per-payment scope</Badge>}
      />

      <Card className="space-y-2" data-testid="refunds-scope-note">
        <p className="text-sm text-pf-ink">
          PayFlow does not expose a global merchant refund list endpoint. This page lists{' '}
          <strong>CAPTURED</strong> payments (paginated) so you can open each payment&apos;s refund
          history and create full or partial refunds there.
        </p>
        <p className="text-xs text-pf-muted">
          No unbounded fetch of all payments or refunds. Backend capacity locking remains
          authoritative.
        </p>
      </Card>

      {paymentsQuery.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : null}

      {paymentsQuery.isError ? (
        <ErrorState
          title="Unable to load captured payments"
          description={
            paymentsQuery.error instanceof Error
              ? paymentsQuery.error.message
              : 'Request failed.'
          }
          action={
            <Button variant="secondary" onClick={() => void paymentsQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : null}

      {!paymentsQuery.isLoading &&
      !paymentsQuery.isError &&
      (paymentsQuery.data?.content.length ?? 0) === 0 ? (
        <EmptyState
          title="No captured payments"
          description="Capture a simulated payment first, then return here or open the payment detail refunds panel."
          icon={<RotateCcw className="h-5 w-5" />}
          action={
            <Button onClick={() => navigate('/payments')}>Go to payments</Button>
          }
        />
      ) : null}

      {!paymentsQuery.isLoading &&
      !paymentsQuery.isError &&
      paymentsQuery.data &&
      paymentsQuery.data.content.length > 0 ? (
        <Card className="overflow-x-auto p-0" data-testid="refundable-payments-table">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-pf-border bg-slate-50 text-xs text-pf-muted uppercase">
              <tr>
                <th className="px-4 py-3 font-medium">Payment</th>
                <th className="px-4 py-3 font-medium">Amount</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Captured</th>
                <th className="px-4 py-3 font-medium">Action</th>
              </tr>
            </thead>
            <tbody>
              {paymentsQuery.data.content.map((payment) => (
                <tr key={payment.paymentId} className="border-b border-pf-border last:border-0">
                  <td className="px-4 py-3">
                    <p className="font-medium">{payment.merchantReference}</p>
                    <p className="font-mono text-xs text-pf-muted">{payment.paymentId}</p>
                  </td>
                  <td className="px-4 py-3 tabular-nums">
                    {formatInrMinor(payment.amountMinor)}
                  </td>
                  <td className="px-4 py-3">
                    <PaymentStatusBadge status={payment.status} />
                  </td>
                  <td className="px-4 py-3 text-pf-muted">
                    {formatTimestamp(payment.capturedAt)}
                  </td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/payments/${payment.paymentId}`}
                      className="text-sm font-medium text-pf-primary hover:underline"
                    >
                      Manage refunds
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-pf-border px-4 py-3">
            <p className="text-xs text-pf-muted">
              Page {paymentsQuery.data.page + 1} of{' '}
              {Math.max(paymentsQuery.data.totalPages, 1)}
            </p>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={page + 1 >= paymentsQuery.data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        </Card>
      ) : null}
    </div>
  );
}
