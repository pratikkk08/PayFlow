import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { ApiClientError } from '@/lib/apiClient';
import { formatInrMinor } from '@/features/analytics/money';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import {
  createIdempotencyKey,
  isUncertainMutationError,
} from '@/features/payments/idempotency';
import { invalidateMerchantFinancialQueries } from '@/features/payments/invalidateFinancialQueries';
import { createPayment } from '@/features/payments/paymentApi';
import { validateCreatePaymentForm } from '@/features/payments/validation';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { PageHeader } from '@/components/ui/PageHeader';
import type { CreatePaymentRequest } from '@/types/api';

type Intent = {
  key: string;
  payload: CreatePaymentRequest;
};

export function CreatePaymentPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { selectedMerchant, selectedMerchantId } = useMerchantSelection();

  const [merchantReference, setMerchantReference] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<ReturnType<typeof validateCreatePaymentForm>['errors']>(
    {},
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [successHint, setSuccessHint] = useState<string | null>(null);
  const intentRef = useRef<Intent | null>(null);

  if (!selectedMerchantId || !selectedMerchant) {
    return (
      <div>
        <PageHeader title="Create payment" description="Select a merchant first." />
        <EmptyState
          title="No merchant selected"
          description="Choose a merchant from the top bar before creating a simulated payment."
          action={
            <Link to="/merchants">
              <Button variant="secondary">Open merchants</Button>
            </Link>
          }
        />
      </div>
    );
  }

  // Locals so nested handlers see narrowed string types (TS closure narrowing).
  const merchantId = selectedMerchantId;
  const merchantName = selectedMerchant.name;

  async function submitIntent(intent: Intent) {
    setSubmitting(true);
    setFormError(null);
    setUncertain(false);
    try {
      const payment = await createPayment(intent.payload, intent.key);
      invalidateMerchantFinancialQueries(queryClient, intent.payload.merchantId);
      intentRef.current = null;
      setSuccessHint(`Created ${payment.merchantReference} (${formatInrMinor(payment.amountMinor)})`);
      navigate(`/payments/${payment.paymentId}`, {
        replace: true,
        state: { created: true },
      });
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
        setFormError(mapCreatePaymentError(error));
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

    const { errors, amountMinor } = validateCreatePaymentForm({
      merchantReference,
      amount,
      description,
    });
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || amountMinor == null) {
      return;
    }

    const payload: CreatePaymentRequest = {
      merchantId,
      merchantReference: merchantReference.trim(),
      amountMinor,
      currency: 'INR',
      description: description.trim() ? description.trim() : null,
    };
    const intent: Intent = {
      key: createIdempotencyKey(),
      payload,
    };
    intentRef.current = intent;
    await submitIntent(intent);
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <PageHeader
        title="Create payment"
        description="Creates a SIMULATED payment intent in CREATED status. No cards or bank credentials."
        actions={<Badge tone="warning">Simulated</Badge>}
      />

      <Card>
        <form className="space-y-4" onSubmit={(e) => void handleSubmit(e)} noValidate>
          <p className="text-sm text-pf-muted">
            Merchant: <span className="font-medium text-pf-ink">{merchantName}</span>
          </p>
          <Input
            label="Merchant reference"
            name="merchantReference"
            value={merchantReference}
            onChange={(e) => setMerchantReference(e.target.value)}
            error={fieldErrors.merchantReference}
            disabled={submitting || uncertain}
            required
            hint="Unique per merchant (max 64). Used for sandbox correlation — not a bank reference."
          />
          <Input
            label="Amount (INR)"
            name="amount"
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            error={fieldErrors.amount}
            disabled={submitting || uncertain}
            required
            hint="Converted to integer paise on the client without floating-point rounding."
            placeholder="1250.50"
          />
          <div className="flex w-full flex-col gap-1.5">
            <label htmlFor="payment-description" className="text-sm font-medium text-pf-ink-soft">
              Description (optional)
            </label>
            <textarea
              id="payment-description"
              name="description"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={submitting || uncertain}
              aria-invalid={fieldErrors.description ? true : undefined}
              className={`w-full rounded-pf border bg-pf-panel px-3 py-2 text-sm shadow-pf-sm ${
                fieldErrors.description ? 'border-pf-danger' : 'border-pf-border'
              }`}
            />
            {fieldErrors.description ? (
              <p className="text-xs text-pf-danger" role="alert">
                {fieldErrors.description}
              </p>
            ) : null}
          </div>

          {formError ? (
            <p className="rounded-pf bg-pf-danger-soft px-3 py-2 text-sm text-pf-danger" role="alert">
              {formError}
            </p>
          ) : null}
          {successHint ? (
            <p className="text-sm text-pf-success" role="status">
              {successHint}
            </p>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={submitting}>
              {submitting
                ? 'Submitting…'
                : uncertain
                  ? 'Retry same request'
                  : 'Create simulated payment'}
            </Button>
            {uncertain ? (
              <Button
                type="button"
                variant="secondary"
                disabled={submitting}
                onClick={() => {
                  intentRef.current = null;
                  setUncertain(false);
                  setFormError(null);
                }}
              >
                Discard uncertain attempt
              </Button>
            ) : (
              <Button type="button" variant="secondary" disabled={submitting} onClick={() => navigate('/payments')}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </Card>
    </div>
  );
}

function mapCreatePaymentError(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.status === 409) {
      return error.message || 'Conflict — reference or idempotency key already used.';
    }
    if (error.status === 400) {
      return error.message || 'Validation failed.';
    }
    if (error.status === 401 || error.status === 403) {
      return 'You are not allowed to create payments for this merchant.';
    }
    if (error.status === 404) {
      return 'Merchant not found.';
    }
    return error.message;
  }
  return error instanceof Error ? error.message : 'Create payment failed.';
}
