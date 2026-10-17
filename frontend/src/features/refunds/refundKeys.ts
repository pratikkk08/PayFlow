import { merchantKeys } from '@/features/merchants/merchantKeys';

/** Merchant-scoped refund keys — cleared when merchants switch. */
export const refundKeys = {
  byPayment: (merchantId: string, paymentId: string, page: number, size: number) =>
    [...merchantKeys.scoped(merchantId), 'refunds', 'byPayment', paymentId, page, size] as const,
  detail: (merchantId: string, refundId: string) =>
    [...merchantKeys.scoped(merchantId), 'refunds', 'detail', refundId] as const,
};
