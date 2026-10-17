import { vi } from 'vitest';
import type {
  CreatePaymentRequest,
  PaymentPageResponse,
  PaymentResponse,
  PaymentStatus,
} from '@/types/api';
import { mockMerchants } from '@/test/merchantMocks';

export const mockPayments: PaymentResponse[] = [
  {
    paymentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    merchantId: mockMerchants[0].id,
    merchantReference: 'order_1001',
    amountMinor: 12_500,
    currency: 'INR',
    status: 'CREATED',
    failureCode: null,
    authorizedAt: null,
    capturedAt: null,
    cancelledAt: null,
    createdAt: '2026-10-16T10:00:00Z',
  },
  {
    paymentId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    merchantId: mockMerchants[0].id,
    merchantReference: 'order_1002',
    amountMinor: 50_000,
    currency: 'INR',
    status: 'CAPTURED',
    failureCode: null,
    authorizedAt: '2026-10-15T11:00:00Z',
    capturedAt: '2026-10-15T11:05:00Z',
    cancelledAt: null,
    createdAt: '2026-10-15T10:00:00Z',
  },
];

export const paymentApiMocks = {
  createPayment: vi.fn(),
  listPayments: vi.fn(),
  getPayment: vi.fn(),
  authorizePayment: vi.fn(),
  capturePayment: vi.fn(),
  cancelPayment: vi.fn(),
};

let store: PaymentResponse[] = [];

function pageOf(
  content: PaymentResponse[],
  page = 0,
  size = 20,
): PaymentPageResponse {
  return {
    content,
    page,
    size,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
  };
}

function upsert(payment: PaymentResponse) {
  const index = store.findIndex((p) => p.paymentId === payment.paymentId);
  if (index >= 0) {
    store[index] = payment;
  } else {
    store.push(payment);
  }
}

export function resetPaymentApiMocks(options?: {
  payments?: PaymentResponse[];
}) {
  store = structuredClone(options?.payments ?? mockPayments);
  paymentApiMocks.createPayment.mockReset();
  paymentApiMocks.listPayments.mockReset();
  paymentApiMocks.getPayment.mockReset();
  paymentApiMocks.authorizePayment.mockReset();
  paymentApiMocks.capturePayment.mockReset();
  paymentApiMocks.cancelPayment.mockReset();

  paymentApiMocks.listPayments.mockImplementation(
    async (params: {
      merchantId: string;
      status?: PaymentStatus;
      page?: number;
      size?: number;
    }) => {
      let filtered = store.filter((p) => p.merchantId === params.merchantId);
      if (params.status) {
        filtered = filtered.filter((p) => p.status === params.status);
      }
      return pageOf(filtered, params.page ?? 0, params.size ?? 20);
    },
  );

  paymentApiMocks.getPayment.mockImplementation(async (paymentId: string) => {
    const found = store.find((p) => p.paymentId === paymentId);
    if (!found) {
      throw Object.assign(new Error('not found'), { status: 404, code: 'not_found' });
    }
    return { ...found };
  });

  paymentApiMocks.createPayment.mockImplementation(
    async (payload: CreatePaymentRequest) => {
      const created: PaymentResponse = {
        paymentId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
        merchantId: payload.merchantId,
        merchantReference: payload.merchantReference,
        amountMinor: payload.amountMinor,
        currency: 'INR',
        status: 'CREATED',
        failureCode: null,
        authorizedAt: null,
        capturedAt: null,
        cancelledAt: null,
        createdAt: '2026-10-17T12:00:00Z',
      };
      upsert(created);
      return created;
    },
  );

  paymentApiMocks.authorizePayment.mockImplementation(async (paymentId: string) => {
    const existing = store.find((p) => p.paymentId === paymentId);
    if (!existing) {
      throw Object.assign(new Error('not found'), { status: 404, code: 'not_found' });
    }
    const next: PaymentResponse = {
      ...existing,
      status: 'AUTHORIZED',
      authorizedAt: '2026-10-17T12:01:00Z',
    };
    upsert(next);
    return next;
  });

  paymentApiMocks.capturePayment.mockImplementation(async (paymentId: string) => {
    const existing = store.find((p) => p.paymentId === paymentId);
    if (!existing) {
      throw Object.assign(new Error('not found'), { status: 404, code: 'not_found' });
    }
    const next: PaymentResponse = {
      ...existing,
      status: 'CAPTURED',
      authorizedAt: existing.authorizedAt ?? '2026-10-17T12:01:00Z',
      capturedAt: '2026-10-17T12:02:00Z',
    };
    upsert(next);
    return next;
  });

  paymentApiMocks.cancelPayment.mockImplementation(async (paymentId: string) => {
    const existing = store.find((p) => p.paymentId === paymentId);
    if (!existing) {
      throw Object.assign(new Error('not found'), { status: 404, code: 'not_found' });
    }
    const next: PaymentResponse = {
      ...existing,
      status: 'CANCELLED',
      cancelledAt: '2026-10-17T12:03:00Z',
    };
    upsert(next);
    return next;
  });
}
