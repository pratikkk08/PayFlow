import { parseInrMajorToMinor } from '@/features/payments/moneyConvert';

export type CreatePaymentFieldErrors = {
  merchantReference?: string;
  amount?: string;
  description?: string;
};

export function validateCreatePaymentForm(values: {
  merchantReference: string;
  amount: string;
  description: string;
}): { errors: CreatePaymentFieldErrors; amountMinor?: number } {
  const errors: CreatePaymentFieldErrors = {};
  const merchantReference = values.merchantReference.trim();
  if (!merchantReference) {
    errors.merchantReference = 'Merchant reference is required.';
  } else if (merchantReference.length > 64) {
    errors.merchantReference = 'Merchant reference must be at most 64 characters.';
  }

  const parsed = parseInrMajorToMinor(values.amount);
  if (!parsed.ok) {
    errors.amount = parsed.error;
  }

  if (values.description.trim().length > 500) {
    errors.description = 'Description must be at most 500 characters.';
  }

  return {
    errors,
    amountMinor: parsed.ok ? parsed.amountMinor : undefined,
  };
}
