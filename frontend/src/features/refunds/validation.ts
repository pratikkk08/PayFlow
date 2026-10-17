import { parseInrMajorToMinor } from '@/features/payments/moneyConvert';

export type CreateRefundFieldErrors = {
  merchantReference?: string;
  amount?: string;
  reason?: string;
};

export function validateCreateRefundForm(
  values: { merchantReference: string; amount: string; reason: string },
  remainingRefundableMinor: number,
): { errors: CreateRefundFieldErrors; amountMinor?: number } {
  const errors: CreateRefundFieldErrors = {};
  const merchantReference = values.merchantReference.trim();
  if (!merchantReference) {
    errors.merchantReference = 'Refund reference is required.';
  } else if (merchantReference.length > 64) {
    errors.merchantReference = 'Refund reference must be at most 64 characters.';
  }

  const parsed = parseInrMajorToMinor(values.amount);
  if (!parsed.ok) {
    errors.amount = parsed.error;
  } else if (parsed.amountMinor > remainingRefundableMinor) {
    errors.amount =
      remainingRefundableMinor <= 0
        ? 'No refundable capacity remains on this payment.'
        : `Amount exceeds remaining refundable capacity.`;
  }

  if (values.reason.trim().length > 500) {
    errors.reason = 'Reason must be at most 500 characters.';
  }

  return {
    errors,
    amountMinor: parsed.ok ? parsed.amountMinor : undefined,
  };
}

/** PENDING + SUCCEEDED reserve capacity (matches backend CAPACITY_RESERVING_STATUSES). */
export function remainingRefundableMinor(
  paymentAmountMinor: number,
  refunds: Array<{ amountMinor: number; status: string }>,
): number {
  const reserved = refunds
    .filter((r) => r.status === 'PENDING' || r.status === 'SUCCEEDED')
    .reduce((sum, r) => sum + r.amountMinor, 0);
  return Math.max(0, paymentAmountMinor - reserved);
}
