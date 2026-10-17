import { merchantKeys } from '@/features/merchants/merchantKeys';

/** Merchant-scoped API key list keys — never store create-response secrets here. */
export const apiKeyKeys = {
  list: (merchantId: string) => [...merchantKeys.scoped(merchantId), 'api-keys', 'list'] as const,
};
