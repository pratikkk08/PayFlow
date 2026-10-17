import { vi } from 'vitest';
import type { MerchantResponse } from '@/types/api';
import { mockUser } from '@/test/authMocks';

export const mockMerchants: MerchantResponse[] = [
  {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Acme Sandbox',
    description: 'Primary test merchant',
    status: 'ACTIVE',
    ownerUserId: mockUser.id,
    createdAt: '2026-01-02T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  },
  {
    id: '33333333-3333-3333-3333-333333333333',
    name: 'Beta Labs',
    description: null,
    status: 'ACTIVE',
    ownerUserId: mockUser.id,
    createdAt: '2026-01-03T00:00:00Z',
    updatedAt: '2026-01-03T00:00:00Z',
  },
];

export const merchantApiMocks = {
  listMerchants: vi.fn(),
  getMerchant: vi.fn(),
  createMerchant: vi.fn(),
  updateMerchant: vi.fn(),
};

export function resetMerchantApiMocks(options?: { merchants?: MerchantResponse[] }) {
  const merchants = options?.merchants ?? mockMerchants;
  merchantApiMocks.listMerchants.mockReset();
  merchantApiMocks.getMerchant.mockReset();
  merchantApiMocks.createMerchant.mockReset();
  merchantApiMocks.updateMerchant.mockReset();

  merchantApiMocks.listMerchants.mockResolvedValue(merchants);
  merchantApiMocks.getMerchant.mockImplementation(async (id: string) => {
    const found = merchants.find((m) => m.id === id);
    if (!found) {
      throw Object.assign(new Error('not found'), { status: 404, code: 'not_found' });
    }
    return found;
  });
  merchantApiMocks.createMerchant.mockImplementation(async (payload: { name: string; description?: string | null }) => ({
    id: '44444444-4444-4444-4444-444444444444',
    name: payload.name,
    description: payload.description ?? null,
    status: 'ACTIVE' as const,
    ownerUserId: mockUser.id,
    createdAt: '2026-01-04T00:00:00Z',
    updatedAt: '2026-01-04T00:00:00Z',
  }));
  merchantApiMocks.updateMerchant.mockImplementation(
    async (id: string, payload: { name?: string; description?: string | null }) => {
      const existing = merchants.find((m) => m.id === id) ?? mockMerchants[0];
      return {
        ...existing,
        id,
        name: payload.name ?? existing.name,
        description:
          payload.description === undefined ? existing.description : payload.description,
        updatedAt: '2026-01-05T00:00:00Z',
      };
    },
  );
}
