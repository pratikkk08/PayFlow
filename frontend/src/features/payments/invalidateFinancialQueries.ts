import type { QueryClient } from '@tanstack/react-query';
import { merchantKeys } from '@/features/merchants/merchantKeys';

/** Drop merchant-scoped payment/refund/analytics caches after successful mutations. */
export function invalidateMerchantFinancialQueries(
  queryClient: QueryClient,
  merchantId: string,
): void {
  void queryClient.invalidateQueries({ queryKey: merchantKeys.scoped(merchantId) });
}
