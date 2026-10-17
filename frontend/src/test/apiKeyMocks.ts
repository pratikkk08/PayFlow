import { vi } from 'vitest';
import type {
  ApiKeyCreatedResponse,
  ApiKeyMetadataResponse,
  CreateApiKeyRequest,
} from '@/types/api';
import { mockMerchants } from '@/test/merchantMocks';

const merchantId = mockMerchants[0].id;

export const mockApiKeys: ApiKeyMetadataResponse[] = [
  {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    merchantId,
    name: 'Primary secret',
    type: 'SECRET',
    mode: 'TEST',
    keyPrefix: 'sk_test_abc12345',
    createdAt: '2026-02-01T10:00:00Z',
    lastUsedAt: '2026-02-02T12:00:00Z',
    revokedAt: null,
  },
  {
    id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    merchantId,
    name: 'Old publishable',
    type: 'PUBLISHABLE',
    mode: 'TEST',
    keyPrefix: 'pk_test_xyz98765',
    createdAt: '2026-01-15T08:00:00Z',
    lastUsedAt: null,
    revokedAt: '2026-01-20T09:00:00Z',
  },
];

export const apiKeyApiMocks = {
  listApiKeys: vi.fn(),
  createApiKey: vi.fn(),
  revokeApiKey: vi.fn(),
};

let keysStore: ApiKeyMetadataResponse[] = [...mockApiKeys];

export function resetApiKeyApiMocks(options?: { keys?: ApiKeyMetadataResponse[] }) {
  keysStore = [...(options?.keys ?? mockApiKeys)];
  apiKeyApiMocks.listApiKeys.mockReset();
  apiKeyApiMocks.createApiKey.mockReset();
  apiKeyApiMocks.revokeApiKey.mockReset();

  apiKeyApiMocks.listApiKeys.mockImplementation(async (id: string) =>
    keysStore.filter((key) => key.merchantId === id),
  );

  apiKeyApiMocks.createApiKey.mockImplementation(
    async (id: string, payload: CreateApiKeyRequest): Promise<ApiKeyCreatedResponse> => {
      const type = payload.type ?? 'SECRET';
      const prefix = type === 'SECRET' ? 'sk_test_newkey12' : 'pk_test_newkey12';
      const secret =
        type === 'SECRET'
          ? 'sk_test_newkey12_ONCE_ONLY_SECRET_VALUE'
          : 'pk_test_newkey12_ONCE_ONLY_PUBLISHABLE';
      const created: ApiKeyCreatedResponse = {
        id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
        merchantId: id,
        name: payload.name,
        type,
        mode: 'TEST',
        keyPrefix: prefix,
        secret,
        createdAt: '2026-03-01T00:00:00Z',
      };
      keysStore = [
        {
          id: created.id,
          merchantId: created.merchantId,
          name: created.name,
          type: created.type,
          mode: created.mode,
          keyPrefix: created.keyPrefix,
          createdAt: created.createdAt,
          lastUsedAt: null,
          revokedAt: null,
        },
        ...keysStore,
      ];
      return created;
    },
  );

  apiKeyApiMocks.revokeApiKey.mockImplementation(async (_merchantId: string, keyId: string) => {
    keysStore = keysStore.map((key) =>
      key.id === keyId ? { ...key, revokedAt: '2026-03-02T00:00:00Z' } : key,
    );
  });
}
