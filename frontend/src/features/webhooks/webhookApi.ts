import { apiClient } from '@/lib/apiClient';
import type {
  CreateWebhookEndpointRequest,
  UpdateWebhookEndpointRequest,
  WebhookEndpointCreatedResponse,
  WebhookEndpointResponse,
} from '@/types/api';

export async function listWebhooks(merchantId: string): Promise<WebhookEndpointResponse[]> {
  const { data } = await apiClient.get<WebhookEndpointResponse[]>(
    `/api/v1/merchants/${merchantId}/webhooks`,
  );
  return data;
}

export async function createWebhook(
  merchantId: string,
  payload: CreateWebhookEndpointRequest,
): Promise<WebhookEndpointCreatedResponse> {
  const { data } = await apiClient.post<WebhookEndpointCreatedResponse>(
    `/api/v1/merchants/${merchantId}/webhooks`,
    payload,
  );
  return data;
}

export async function updateWebhook(
  merchantId: string,
  webhookId: string,
  payload: UpdateWebhookEndpointRequest,
): Promise<WebhookEndpointResponse> {
  const { data } = await apiClient.patch<WebhookEndpointResponse>(
    `/api/v1/merchants/${merchantId}/webhooks/${webhookId}`,
    payload,
  );
  return data;
}

export async function deleteWebhook(merchantId: string, webhookId: string): Promise<void> {
  await apiClient.delete(`/api/v1/merchants/${merchantId}/webhooks/${webhookId}`);
}
