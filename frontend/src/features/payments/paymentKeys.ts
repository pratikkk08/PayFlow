import { merchantKeys } from '@/features/merchants/merchantKeys';

export type PaymentListParams = {
  status?: string;
  createdFrom?: string;
  createdTo?: string;
  page: number;
  size: number;
};

/** Merchant-scoped payment keys — cleared when the top-bar selector switches merchants. */
export const paymentKeys = {
  lists: (merchantId: string) => [...merchantKeys.scoped(merchantId), 'payments', 'list'] as const,
  list: (merchantId: string, params: PaymentListParams) =>
    [...paymentKeys.lists(merchantId), params] as const,
  details: (merchantId: string) =>
    [...merchantKeys.scoped(merchantId), 'payments', 'detail'] as const,
  detail: (merchantId: string, paymentId: string) =>
    [...paymentKeys.details(merchantId), paymentId] as const,
};
