import { apiClient } from '@/lib/apiClient';
import type {
  CreateMerchantRequest,
  MerchantResponse,
  UpdateMerchantRequest,
} from '@/types/api';

export async function listMerchants(): Promise<MerchantResponse[]> {
  const { data } = await apiClient.get<MerchantResponse[]>('/api/v1/merchants');
  return data;
}

export async function getMerchant(merchantId: string): Promise<MerchantResponse> {
  const { data } = await apiClient.get<MerchantResponse>(`/api/v1/merchants/${merchantId}`);
  return data;
}

export async function createMerchant(
  payload: CreateMerchantRequest,
): Promise<MerchantResponse> {
  const { data } = await apiClient.post<MerchantResponse>('/api/v1/merchants', payload);
  return data;
}

export async function updateMerchant(
  merchantId: string,
  payload: UpdateMerchantRequest,
): Promise<MerchantResponse> {
  const { data } = await apiClient.patch<MerchantResponse>(
    `/api/v1/merchants/${merchantId}`,
    payload,
  );
  return data;
}
