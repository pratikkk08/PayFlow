import { merchantKeys } from '@/features/merchants/merchantKeys';

/** Merchant-scoped webhook list keys — never store create-response secrets here. */
export const webhookKeys = {
  list: (merchantId: string) => [...merchantKeys.scoped(merchantId), 'webhooks', 'list'] as const,
};
