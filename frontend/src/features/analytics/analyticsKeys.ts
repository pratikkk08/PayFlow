import { merchantKeys } from '@/features/merchants/merchantKeys';

/** Merchant-scoped analytics keys — cleared when the top-bar selector switches merchants. */
export const analyticsKeys = {
  overview: (merchantId: string, from: string, to: string) =>
    [...merchantKeys.scoped(merchantId), 'analytics', 'overview', from, to] as const,
  daily: (merchantId: string, from: string, to: string) =>
    [...merchantKeys.scoped(merchantId), 'analytics', 'daily', from, to] as const,
};
