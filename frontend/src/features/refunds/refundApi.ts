import { apiClient } from '@/lib/apiClient';
import type { CreateRefundRequest, RefundPageResponse, RefundResponse } from '@/types/api';

export async function createRefund(
  paymentId: string,
  payload: CreateRefundRequest,
  idempotencyKey: string,
): Promise<RefundResponse> {
  const { data } = await apiClient.post<RefundResponse>(
    `/api/v1/payments/${paymentId}/refunds`,
    payload,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  );
  return data;
}

export async function listRefundsForPayment(
  paymentId: string,
  page = 0,
  size = 20,
): Promise<RefundPageResponse> {
  const { data } = await apiClient.get<RefundPageResponse>(
    `/api/v1/payments/${paymentId}/refunds`,
    { params: { page, size } },
  );
  return data;
}

export async function getRefund(refundId: string): Promise<RefundResponse> {
  const { data } = await apiClient.get<RefundResponse>(`/api/v1/refunds/${refundId}`);
  return data;
}
