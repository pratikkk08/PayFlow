import { vi } from 'vitest';
import type {
  CreateWebhookEndpointRequest,
  UpdateWebhookEndpointRequest,
  WebhookEndpointCreatedResponse,
  WebhookEndpointResponse,
} from '@/types/api';
import { mockMerchants } from '@/test/merchantMocks';

const merchantId = mockMerchants[0].id;

export const mockWebhooks: WebhookEndpointResponse[] = [
  {
    id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
    merchantId,
    url: 'https://hooks.example.com/payflow',
    secretPrefix: 'whsec_abcdefgh',
    enabled: true,
    eventTypes: ['payment.captured', 'refund.succeeded'],
    createdAt: '2026-02-10T10:00:00Z',
    updatedAt: '2026-02-10T10:00:00Z',
    secretRotatedAt: '2026-02-10T10:00:00Z',
    version: 0,
  },
];

export const webhookApiMocks = {
  listWebhooks: vi.fn(),
  createWebhook: vi.fn(),
  updateWebhook: vi.fn(),
  deleteWebhook: vi.fn(),
};

let webhookStore: WebhookEndpointResponse[] = [...mockWebhooks];

export function resetWebhookApiMocks(options?: { webhooks?: WebhookEndpointResponse[] }) {
  webhookStore = [...(options?.webhooks ?? mockWebhooks)];
  webhookApiMocks.listWebhooks.mockReset();
  webhookApiMocks.createWebhook.mockReset();
  webhookApiMocks.updateWebhook.mockReset();
  webhookApiMocks.deleteWebhook.mockReset();

  webhookApiMocks.listWebhooks.mockImplementation(async (id: string) =>
    webhookStore.filter((hook) => hook.merchantId === id),
  );

  webhookApiMocks.createWebhook.mockImplementation(
    async (
      id: string,
      payload: CreateWebhookEndpointRequest,
    ): Promise<WebhookEndpointCreatedResponse> => {
      const created: WebhookEndpointCreatedResponse = {
        id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        merchantId: id,
        url: payload.url,
        secretPrefix: 'whsec_newsecret1',
        signingSecret: 'whsec_newsecret1_ONCE_ONLY_SIGNING_SECRET',
        enabled: payload.enabled ?? true,
        eventTypes: [...payload.eventTypes],
        createdAt: '2026-03-01T00:00:00Z',
        updatedAt: '2026-03-01T00:00:00Z',
        secretRotatedAt: '2026-03-01T00:00:00Z',
        version: 0,
      };
      webhookStore = [
        {
          id: created.id,
          merchantId: created.merchantId,
          url: created.url,
          secretPrefix: created.secretPrefix,
          enabled: created.enabled,
          eventTypes: created.eventTypes,
          createdAt: created.createdAt,
          updatedAt: created.updatedAt,
          secretRotatedAt: created.secretRotatedAt,
          version: created.version,
        },
        ...webhookStore,
      ];
      return created;
    },
  );

  webhookApiMocks.updateWebhook.mockImplementation(
    async (
      _merchantId: string,
      webhookId: string,
      payload: UpdateWebhookEndpointRequest,
    ): Promise<WebhookEndpointResponse> => {
      const existing = webhookStore.find((hook) => hook.id === webhookId);
      if (!existing) {
        throw Object.assign(new Error('not found'), { status: 404, code: 'not_found' });
      }
      const updated: WebhookEndpointResponse = {
        ...existing,
        url: payload.url ?? existing.url,
        eventTypes: payload.eventTypes ?? existing.eventTypes,
        enabled: payload.enabled ?? existing.enabled,
        updatedAt: '2026-03-02T00:00:00Z',
        version: existing.version + 1,
      };
      webhookStore = webhookStore.map((hook) => (hook.id === webhookId ? updated : hook));
      return updated;
    },
  );

  webhookApiMocks.deleteWebhook.mockImplementation(async (_merchantId: string, webhookId: string) => {
    webhookStore = webhookStore.filter((hook) => hook.id !== webhookId);
  });
}
