import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiClientError } from '@/lib/apiClient';
import { formatInrMinor } from '@/features/analytics/money';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { formatTimestamp } from '@/features/payments/formatTimestamp';
import { invalidateMerchantFinancialQueries } from '@/features/payments/invalidateFinancialQueries';
import {
  authorizePayment,
  cancelPayment,
  capturePayment,
  getPayment,
} from '@/features/payments/paymentApi';
import { paymentKeys } from '@/features/payments/paymentKeys';
import { PaymentStatusBadge } from '@/features/payments/PaymentStatusBadge';
import { PaymentRefundsPanel } from '@/features/refunds/PaymentRefundsPanel';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { PageHeader } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeleton';
import type { PaymentResponse } from '@/types/api';

export function PaymentDetailPage() {
  const { paymentId = '' } = useParams();
  const queryClient = useQueryClient();
  const { selectedMerchantId, selectedMerchant } = useMerchantSelection();

  const [actionError, setActionError] = useState<string | null>(null);
  const [heldForReview, setHeldForReview] = useState(false);
  const [busyAction, setBusyAction] = useState<'authorize' | 'capture' | 'cancel' | null>(null);
  const [confirm, setConfirm] = useState<'capture' | 'cancel' | null>(null);

  const paymentQuery = useQuery({
    queryKey: paymentKeys.detail(selectedMerchantId ?? 'none', paymentId),
    queryFn: () => getPayment(paymentId),
    enabled: Boolean(paymentId && selectedMerchantId),
  });

  const payment = paymentQuery.data;

  async function refreshAfterConflict() {
    await paymentQuery.refetch();
  }

  async function runAction(action: 'authorize' | 'capture' | 'cancel') {
    if (!payment || !selectedMerchantId) {
      return;
    }
    setBusyAction(action);
    setActionError(null);
    try {
      let next: PaymentResponse;
      if (action === 'authorize') {
        next = await authorizePayment(payment.paymentId);
        // REVIEW hold: status remains CREATED — no manual approval API exists.
        if (next.status === 'CREATED') {
          setHeldForReview(true);
        } else {
          setHeldForReview(false);
        }
      } else if (action === 'capture') {
        next = await capturePayment(payment.paymentId);
        setHeldForReview(false);
      } else {
        next = await cancelPayment(payment.paymentId);
        setHeldForReview(false);
      }
      queryClient.setQueryData(
        paymentKeys.detail(selectedMerchantId, payment.paymentId),
        next,
      );
      invalidateMerchantFinancialQueries(queryClient, selectedMerchantId);
    } catch (error) {
      if (error instanceof ApiClientError && error.code === 'invalid_state_transition') {
        setActionError(`${error.message} Refreshing payment state.`);
        await refreshAfterConflict();
        if (error.message.toLowerCase().includes('risk review')) {
          setHeldForReview(true);
        }
      } else {
        setActionError(error instanceof Error ? error.message : 'Action failed.');
      }
    } finally {
      setBusyAction(null);
      setConfirm(null);
    }
  }

  if (!selectedMerchantId) {
    return (
      <EmptyState
        title="Select a merchant"
        description="Payment details are merchant-scoped in the console cache."
        action={
          <Link to="/merchants">
            <Button variant="secondary">Open merchants</Button>
          </Link>
        }
      />
    );
  }

  if (paymentQuery.isLoading) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Loading payment">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (paymentQuery.isError || !payment) {
    return (
      <ErrorState
        title="Unable to load payment"
        description={
          paymentQuery.error instanceof Error
            ? paymentQuery.error.message
            : 'Payment not found or inaccessible.'
        }
        action={
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => void paymentQuery.refetch()}>
              Retry
            </Button>
            <Link to="/payments">
              <Button variant="secondary">Back to payments</Button>
            </Link>
          </div>
        }
      />
    );
  }

  if (selectedMerchant && payment.merchantId !== selectedMerchantId) {
    return (
      <ErrorState
        title="Merchant mismatch"
        description="This payment belongs to a different merchant than the one currently selected. Switch merchants or open it from the payments list."
        action={
          <Link to="/payments">
            <Button variant="secondary">Back to payments</Button>
          </Link>
        }
      />
    );
  }

  const canAuthorize = payment.status === 'CREATED' && !heldForReview;
  const canCapture = payment.status === 'AUTHORIZED';
  const canCancel = payment.status === 'CREATED' || payment.status === 'AUTHORIZED';

  return (
    <div className="space-y-6">
      <PageHeader
        title={payment.merchantReference}
        description="SIMULATED payment detail — not bank settlement."
        actions={
          <Link to="/payments">
            <Button variant="secondary">Back to list</Button>
          </Link>
        }
      />

      <Card className="space-y-4" data-testid="payment-detail">
        <div className="flex flex-wrap items-center gap-2">
          <PaymentStatusBadge status={payment.status} />
          <Badge tone="warning">Simulated</Badge>
          {heldForReview ? <Badge tone="warning">Held for risk REVIEW</Badge> : null}
        </div>

        {heldForReview ? (
          <p className="rounded-pf bg-pf-warning-soft px-3 py-2 text-sm text-pf-ink" role="status">
            Authorization returned CREATED after risk REVIEW. There is no manual approval UI in
            this console — the payment stays held. Cancel remains available only if the API still
            allows it; further authorize attempts return a conflict.
          </p>
        ) : null}

        <dl className="grid gap-3 sm:grid-cols-2">
          <Detail term="Payment ID" value={payment.paymentId} mono />
          <Detail term="Amount" value={formatInrMinor(payment.amountMinor)} />
          <Detail term="Currency" value={payment.currency} />
          <Detail term="Status" value={payment.status} />
          <Detail term="Created" value={formatTimestamp(payment.createdAt)} />
          <Detail term="Authorized" value={formatTimestamp(payment.authorizedAt)} />
          <Detail term="Captured" value={formatTimestamp(payment.capturedAt)} />
          <Detail term="Cancelled" value={formatTimestamp(payment.cancelledAt)} />
          <Detail
            term="Failure code"
            value={payment.failureCode?.trim() ? payment.failureCode : '—'}
          />
        </dl>
        <p className="text-xs text-pf-muted">
          Processor reference is stored server-side but is not exposed on the payment API response.
        </p>

        {actionError ? (
          <p className="text-sm text-pf-danger" role="alert">
            {actionError}
          </p>
        ) : null}

        <div className="flex flex-wrap gap-2">
          {canAuthorize ? (
            <Button
              disabled={busyAction != null}
              onClick={() => void runAction('authorize')}
            >
              {busyAction === 'authorize' ? 'Authorizing…' : 'Authorize (simulated)'}
            </Button>
          ) : null}
          {canCapture ? (
            <Button
              disabled={busyAction != null}
              onClick={() => setConfirm('capture')}
            >
              Capture (simulated)
            </Button>
          ) : null}
          {canCancel ? (
            <Button
              variant="danger"
              disabled={busyAction != null}
              onClick={() => setConfirm('cancel')}
            >
              Cancel payment
            </Button>
          ) : null}
        </div>
      </Card>

      <PaymentRefundsPanel payment={payment} />

      <ConfirmDialog
        open={confirm === 'capture'}
        title="Capture this payment?"
        description="SIMULATED capture only — marks the payment CAPTURED in PayFlow. No bank settlement or real funds move."
        confirmLabel="Capture"
        confirming={busyAction === 'capture'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => void runAction('capture')}
      />
      <ConfirmDialog
        open={confirm === 'cancel'}
        title="Cancel this payment?"
        description="Cancels the SIMULATED payment intent. This cannot be undone."
        confirmLabel="Cancel payment"
        tone="danger"
        confirming={busyAction === 'cancel'}
        onCancel={() => setConfirm(null)}
        onConfirm={() => void runAction('cancel')}
      />
    </div>
  );
}

function Detail({
  term,
  value,
  mono,
}: {
  term: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div>
      <dt className="text-xs font-medium tracking-wide text-pf-muted uppercase">{term}</dt>
      <dd className={`mt-1 text-sm text-pf-ink ${mono ? 'break-all font-mono text-xs' : ''}`}>
        {value}
      </dd>
    </div>
  );
}
