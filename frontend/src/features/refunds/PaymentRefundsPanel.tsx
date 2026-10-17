import { useRef, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiClientError } from '@/lib/apiClient';
import { formatInrMinor } from '@/features/analytics/money';
import {
  createIdempotencyKey,
  isUncertainMutationError,
} from '@/features/payments/idempotency';
import { invalidateMerchantFinancialQueries } from '@/features/payments/invalidateFinancialQueries';
import { formatTimestamp } from '@/features/payments/formatTimestamp';
import { createRefund, listRefundsForPayment } from '@/features/refunds/refundApi';
import { refundKeys } from '@/features/refunds/refundKeys';
import { RefundStatusBadge } from '@/features/refunds/RefundStatusBadge';
import {
  remainingRefundableMinor,
  validateCreateRefundForm,
} from '@/features/refunds/validation';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Skeleton } from '@/components/ui/Skeleton';
import type { CreateRefundRequest, PaymentResponse } from '@/types/api';

type Intent = {
  key: string;
  payload: CreateRefundRequest;
};

type PaymentRefundsPanelProps = {
  payment: PaymentResponse;
};

export function PaymentRefundsPanel({ payment }: PaymentRefundsPanelProps) {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const size = 20;

  const refundsQuery = useQuery({
    queryKey: refundKeys.byPayment(payment.merchantId, payment.paymentId, page, size),
    queryFn: () => listRefundsForPayment(payment.paymentId, page, size),
  });

  const refunds = refundsQuery.data?.content ?? [];
  // Approximate remaining from loaded pages only — backend locking remains authoritative.
  const remaining = remainingRefundableMinor(payment.amountMinor, refunds);
  const canRefund = payment.status === 'CAPTURED' && remaining > 0;

  const [merchantReference, setMerchantReference] = useState('');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [fieldErrors, setFieldErrors] = useState<
    ReturnType<typeof validateCreateRefundForm>['errors']
  >({});
  const [formError, setFormError] = useState<string | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const intentRef = useRef<Intent | null>(null);

  async function submitIntent(intent: Intent) {
    setSubmitting(true);
    setFormError(null);
    setUncertain(false);
    try {
      await createRefund(payment.paymentId, intent.payload, intent.key);
      invalidateMerchantFinancialQueries(queryClient, payment.merchantId);
      intentRef.current = null;
      setMerchantReference('');
      setAmount('');
      setReason('');
      setFieldErrors({});
      await refundsQuery.refetch();
    } catch (error) {
      if (isUncertainMutationError(error)) {
        setUncertain(true);
        setFormError(
          error instanceof Error
            ? `${error.message} The same Idempotency-Key will be reused on retry.`
            : 'Request may still be in flight. Retry with the same Idempotency-Key.',
        );
      } else {
        intentRef.current = null;
        setFormError(mapRefundError(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    if (uncertain && intentRef.current) {
      await submitIntent(intentRef.current);
      return;
    }

    const { errors, amountMinor } = validateCreateRefundForm(
      { merchantReference, amount, reason },
      remaining,
    );
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || amountMinor == null) {
      return;
    }

    const payload: CreateRefundRequest = {
      merchantReference: merchantReference.trim(),
      amountMinor,
      reason: reason.trim() ? reason.trim() : null,
    };
    const intent: Intent = { key: createIdempotencyKey(), payload };
    intentRef.current = intent;
    await submitIntent(intent);
  }

  return (
    <div className="space-y-4" data-testid="payment-refunds-panel">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-base font-semibold text-pf-ink">Refunds</h3>
        <p className="text-xs text-pf-muted">
          Remaining (from loaded history): {formatInrMinor(remaining)} · SIMULATED only
        </p>
      </div>

      {refundsQuery.isLoading ? (
        <Skeleton className="h-24 w-full" />
      ) : null}

      {refundsQuery.isError ? (
        <ErrorState
          title="Unable to load refunds"
          description={
            refundsQuery.error instanceof Error
              ? refundsQuery.error.message
              : 'Request failed.'
          }
          action={
            <Button variant="secondary" onClick={() => void refundsQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : null}

      {!refundsQuery.isLoading && !refundsQuery.isError && refunds.length === 0 ? (
        <EmptyState
          title="No refunds yet"
          description={
            payment.status === 'CAPTURED'
              ? 'Create a full or partial simulated refund below.'
              : 'Refunds are only available after a payment is CAPTURED.'
          }
        />
      ) : null}

      {!refundsQuery.isLoading && !refundsQuery.isError && refunds.length > 0 ? (
        <Card className="overflow-x-auto p-0">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-pf-border bg-slate-50 text-xs text-pf-muted uppercase">
              <tr>
                <th className="px-4 py-3 font-medium">Reference</th>
                <th className="px-4 py-3 font-medium">Amount</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {refunds.map((refund) => (
                <tr key={refund.refundId} className="border-b border-pf-border last:border-0">
                  <td className="px-4 py-3">
                    <p className="font-medium">{refund.merchantReference}</p>
                    <p className="font-mono text-xs text-pf-muted">{refund.refundId}</p>
                    {refund.failureCode ? (
                      <p className="text-xs text-pf-danger">Failure: {refund.failureCode}</p>
                    ) : null}
                  </td>
                  <td className="px-4 py-3 tabular-nums">{formatInrMinor(refund.amountMinor)}</td>
                  <td className="px-4 py-3">
                    <RefundStatusBadge status={refund.status} />
                  </td>
                  <td className="px-4 py-3 text-pf-muted">{formatTimestamp(refund.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {(refundsQuery.data?.totalPages ?? 0) > 1 ? (
            <div className="flex justify-end gap-2 border-t border-pf-border px-4 py-3">
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
                disabled={page + 1 >= (refundsQuery.data?.totalPages ?? 0)}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          ) : null}
        </Card>
      ) : null}

      {canRefund ? (
        <Card>
          <form className="space-y-3" onSubmit={(e) => void handleSubmit(e)} noValidate>
            <h4 className="text-sm font-semibold text-pf-ink">Create simulated refund</h4>
            <p className="text-xs text-pf-muted">
              Full or partial. Backend capacity locking is authoritative if concurrent refunds
              exist.
            </p>
            <Input
              label="Refund reference"
              value={merchantReference}
              onChange={(e) => setMerchantReference(e.target.value)}
              error={fieldErrors.merchantReference}
              disabled={submitting || uncertain}
              required
            />
            <Input
              label="Amount (INR)"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              error={fieldErrors.amount}
              disabled={submitting || uncertain}
              required
              hint={`Up to ${formatInrMinor(remaining)} remaining (from loaded history).`}
            />
            <Input
              label="Reason (optional)"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              error={fieldErrors.reason}
              disabled={submitting || uncertain}
            />
            {formError ? (
              <p className="text-sm text-pf-danger" role="alert">
                {formError}
              </p>
            ) : null}
            <Button type="submit" disabled={submitting}>
              {submitting
                ? 'Submitting…'
                : uncertain
                  ? 'Retry same refund'
                  : 'Submit simulated refund'}
            </Button>
          </form>
        </Card>
      ) : null}
    </div>
  );
}

function mapRefundError(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.code === 'insufficient_refundable_capacity') {
      return 'Refund exceeds remaining capacity. Refresh and try a smaller amount.';
    }
    if (error.status === 409) {
      return error.message || 'Conflict — refresh payment and refund history.';
    }
    return error.message;
  }
  return error instanceof Error ? error.message : 'Refund failed.';
}
