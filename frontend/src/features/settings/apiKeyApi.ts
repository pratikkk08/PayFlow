import { apiClient } from '@/lib/apiClient';
import type {
  ApiKeyCreatedResponse,
  ApiKeyMetadataResponse,
  CreateApiKeyRequest,
} from '@/types/api';

export async function listApiKeys(merchantId: string): Promise<ApiKeyMetadataResponse[]> {
  const { data } = await apiClient.get<ApiKeyMetadataResponse[]>(
    `/api/v1/merchants/${merchantId}/api-keys`,
  );
  return data;
}

export async function createApiKey(
  merchantId: string,
  payload: CreateApiKeyRequest,
): Promise<ApiKeyCreatedResponse> {
  const { data } = await apiClient.post<ApiKeyCreatedResponse>(
    `/api/v1/merchants/${merchantId}/api-keys`,
    payload,
  );
  return data;
}

export async function revokeApiKey(merchantId: string, keyId: string): Promise<void> {
  await apiClient.delete(`/api/v1/merchants/${merchantId}/api-keys/${keyId}`);
}
