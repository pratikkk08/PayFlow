import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CreditCard, Plus } from 'lucide-react';
import { formatInrMinor } from '@/features/analytics/money';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { listPayments } from '@/features/payments/paymentApi';
import { paymentKeys } from '@/features/payments/paymentKeys';
import { PaymentStatusBadge } from '@/features/payments/PaymentStatusBadge';
import { formatTimestamp } from '@/features/payments/formatTimestamp';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { PageHeader } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeleton';
import type { PaymentStatus } from '@/types/api';

const PAGE_SIZE = 20;

const STATUS_OPTIONS: Array<{ value: '' | PaymentStatus; label: string }> = [
  { value: '', label: 'All statuses' },
  { value: 'CREATED', label: 'CREATED' },
  { value: 'AUTHORIZED', label: 'AUTHORIZED' },
  { value: 'CAPTURED', label: 'CAPTURED' },
  { value: 'FAILED', label: 'FAILED' },
  { value: 'CANCELLED', label: 'CANCELLED' },
];

export function PaymentsPage() {
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

  const [status, setStatus] = useState<'' | PaymentStatus>('');
  const [createdFromDate, setCreatedFromDate] = useState('');
  const [createdToDate, setCreatedToDate] = useState('');
  const [page, setPage] = useState(0);

  const listParams = useMemo(
    () => ({
      status: status || undefined,
      createdFrom: createdFromDate ? `${createdFromDate}T00:00:00Z` : undefined,
      createdTo: createdToDate ? `${createdToDate}T23:59:59.999Z` : undefined,
      page,
      size: PAGE_SIZE,
    }),
    [status, createdFromDate, createdToDate, page],
  );

  const paymentsQuery = useQuery({
    queryKey: paymentKeys.list(selectedMerchantId ?? 'none', listParams),
    queryFn: () =>
      listPayments({
        merchantId: selectedMerchantId!,
        status: listParams.status as PaymentStatus | undefined,
        createdFrom: listParams.createdFrom,
        createdTo: listParams.createdTo,
        page: listParams.page,
        size: listParams.size,
      }),
    enabled: Boolean(selectedMerchantId),
  });

  if (merchantsLoading) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Loading payments">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
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

  if (merchants.length === 0 || !selectedMerchantId || !selectedMerchant) {
    return (
      <div>
        <PageHeader
          title="Payments"
          description="Simulated payment intents for a selected merchant workspace."
        />
        <EmptyState
          title="Select or create a merchant"
          description="Payments are scoped to a merchant. Create a sandbox merchant first."
          icon={<CreditCard className="h-5 w-5" />}
          action={
            <Link to="/merchants/new">
              <Button>Create merchant</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const pageData = paymentsQuery.data;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Payments"
        description={`SIMULATED payment intents for ${selectedMerchant.name}. No live cards or bank settlement.`}
        actions={
          <Button onClick={() => navigate('/payments/new')}>
            <Plus className="h-4 w-4" aria-hidden />
            Create payment
          </Button>
        }
      />

      <Card className="space-y-4" data-testid="payment-filters">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex min-w-[10rem] flex-col gap-1.5">
            <label htmlFor="payment-status" className="text-sm font-medium text-pf-ink-soft">
              Status
            </label>
            <select
              id="payment-status"
              className="rounded-pf border border-pf-border bg-pf-panel px-3 py-2 text-sm"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as '' | PaymentStatus);
                setPage(0);
              }}
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option.label} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <Input
            label="Created from (UTC)"
            type="date"
            value={createdFromDate}
            onChange={(event) => {
              setCreatedFromDate(event.target.value);
              setPage(0);
            }}
          />
          <Input
            label="Created to (UTC)"
            type="date"
            value={createdToDate}
            onChange={(event) => {
              setCreatedToDate(event.target.value);
              setPage(0);
            }}
          />
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              setStatus('');
              setCreatedFromDate('');
              setCreatedToDate('');
              setPage(0);
            }}
          >
            Clear filters
          </Button>
        </div>
        <p className="text-xs text-pf-muted">
          Server-side filters and pagination. Search by merchant reference is not exposed by the
          list API.
        </p>
      </Card>

      {paymentsQuery.isLoading ? (
        <div className="space-y-2" aria-busy="true" aria-label="Loading payment list">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : null}

      {paymentsQuery.isError ? (
        <ErrorState
          title="Unable to load payments"
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
      pageData &&
      pageData.content.length === 0 ? (
        <EmptyState
          title="No simulated payments"
          description="Create a sandbox payment intent for this merchant to begin authorize → capture → refund flows."
          icon={<CreditCard className="h-5 w-5" />}
          action={
            <Button onClick={() => navigate('/payments/new')}>
              <Plus className="h-4 w-4" aria-hidden />
              Create payment
            </Button>
          }
        />
      ) : null}

      {!paymentsQuery.isLoading && !paymentsQuery.isError && pageData && pageData.content.length > 0 ? (
        <Card className="overflow-x-auto p-0" data-testid="payments-table">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-pf-border bg-slate-50 text-xs tracking-wide text-pf-muted uppercase">
              <tr>
                <th className="px-4 py-3 font-medium">Reference</th>
                <th className="px-4 py-3 font-medium">Amount</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {pageData.content.map((payment) => (
                <tr key={payment.paymentId} className="border-b border-pf-border last:border-0">
                  <td className="px-4 py-3">
                    <Link
                      to={`/payments/${payment.paymentId}`}
                      className="font-medium text-pf-primary hover:underline"
                    >
                      {payment.merchantReference}
                    </Link>
                    <p className="mt-0.5 font-mono text-xs text-pf-muted">{payment.paymentId}</p>
                  </td>
                  <td className="px-4 py-3 tabular-nums">
                    {formatInrMinor(payment.amountMinor)}
                  </td>
                  <td className="px-4 py-3">
                    <PaymentStatusBadge status={payment.status} />
                  </td>
                  <td className="px-4 py-3 text-pf-muted">{formatTimestamp(payment.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-pf-border px-4 py-3">
            <p className="text-xs text-pf-muted" data-testid="payments-page-meta">
              Page {pageData.page + 1} of {Math.max(pageData.totalPages, 1)} ·{' '}
              {pageData.totalElements} total
            </p>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={page <= 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={pageData.page + 1 >= pageData.totalPages}
                onClick={() => setPage((current) => current + 1)}
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
