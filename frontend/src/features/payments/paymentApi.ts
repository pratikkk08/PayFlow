import { apiClient } from '@/lib/apiClient';
import type {
  CreatePaymentRequest,
  PaymentPageResponse,
  PaymentResponse,
  PaymentStatus,
} from '@/types/api';

export type ListPaymentsParams = {
  merchantId: string;
  status?: PaymentStatus;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
};

export async function createPayment(
  payload: CreatePaymentRequest,
  idempotencyKey: string,
): Promise<PaymentResponse> {
  const { data } = await apiClient.post<PaymentResponse>('/api/v1/payments', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return data;
}

export async function listPayments(params: ListPaymentsParams): Promise<PaymentPageResponse> {
  const { data } = await apiClient.get<PaymentPageResponse>('/api/v1/payments', {
    params: {
      merchantId: params.merchantId,
      status: params.status,
      createdFrom: params.createdFrom,
      createdTo: params.createdTo,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return data;
}

export async function getPayment(paymentId: string): Promise<PaymentResponse> {
  const { data } = await apiClient.get<PaymentResponse>(`/api/v1/payments/${paymentId}`);
  return data;
}

export async function authorizePayment(paymentId: string): Promise<PaymentResponse> {
  const { data } = await apiClient.post<PaymentResponse>(
    `/api/v1/payments/${paymentId}/authorize`,
  );
  return data;
}

export async function capturePayment(paymentId: string): Promise<PaymentResponse> {
  const { data } = await apiClient.post<PaymentResponse>(`/api/v1/payments/${paymentId}/capture`);
  return data;
}

export async function cancelPayment(paymentId: string): Promise<PaymentResponse> {
  const { data } = await apiClient.post<PaymentResponse>(`/api/v1/payments/${paymentId}/cancel`);
  return data;
}
