import { vi } from 'vitest';
import type {
  CreateRefundRequest,
  RefundPageResponse,
  RefundResponse,
} from '@/types/api';
import { mockMerchants } from '@/test/merchantMocks';
import { mockPayments } from '@/test/paymentMocks';

export const mockRefunds: RefundResponse[] = [
  {
    refundId: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
    paymentId: mockPayments[1].paymentId,
    merchantId: mockMerchants[0].id,
    merchantReference: 'refund_1',
    amountMinor: 10_000,
    currency: 'INR',
    status: 'SUCCEEDED',
    failureCode: null,
    completedAt: '2026-10-15T12:00:00Z',
    createdAt: '2026-10-15T12:00:00Z',
    updatedAt: '2026-10-15T12:00:00Z',
  },
];

export const refundApiMocks = {
  createRefund: vi.fn(),
  listRefundsForPayment: vi.fn(),
  getRefund: vi.fn(),
};

export function resetRefundApiMocks(options?: { refunds?: RefundResponse[] }) {
  const refunds = options?.refunds ?? mockRefunds;
  refundApiMocks.createRefund.mockReset();
  refundApiMocks.listRefundsForPayment.mockReset();
  refundApiMocks.getRefund.mockReset();

  refundApiMocks.listRefundsForPayment.mockImplementation(
    async (paymentId: string, page = 0, size = 20): Promise<RefundPageResponse> => {
      const content = refunds.filter((r) => r.paymentId === paymentId);
      return {
        content,
        page,
        size,
        totalElements: content.length,
        totalPages: content.length === 0 ? 0 : 1,
      };
    },
  );

  refundApiMocks.getRefund.mockImplementation(async (refundId: string) => {
    const found = refunds.find((r) => r.refundId === refundId);
    if (!found) {
      throw Object.assign(new Error('not found'), { status: 404, code: 'not_found' });
    }
    return found;
  });

  refundApiMocks.createRefund.mockImplementation(
    async (paymentId: string, payload: CreateRefundRequest) => ({
      refundId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
      paymentId,
      merchantId: mockMerchants[0].id,
      merchantReference: payload.merchantReference,
      amountMinor: payload.amountMinor,
      currency: 'INR',
      status: 'SUCCEEDED' as const,
      failureCode: null,
      completedAt: '2026-10-17T13:00:00Z',
      createdAt: '2026-10-17T13:00:00Z',
      updatedAt: '2026-10-17T13:00:00Z',
    }),
  );
}
