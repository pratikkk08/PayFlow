import { vi } from 'vitest';
import type { BrowserAccessTokenResponse, UserResponse } from '@/types/api';

export const mockUser: UserResponse = {
  id: '11111111-1111-1111-1111-111111111111',
  fullName: 'Test Operator',
  email: 'operator@example.com',
  roles: ['USER'],
  createdAt: '2026-01-01T00:00:00Z',
};

export const mockTokens: BrowserAccessTokenResponse = {
  accessToken: 'test-access-token',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
  userId: mockUser.id,
};

export const authApiMocks = {
  browserLogin: vi.fn(),
  browserRefresh: vi.fn(),
  browserLogout: vi.fn(),
  registerUser: vi.fn(),
  fetchCurrentUser: vi.fn(),
};

export function resetAuthApiMocks(options?: { restoreSession?: boolean }) {
  authApiMocks.browserLogin.mockReset();
  authApiMocks.browserRefresh.mockReset();
  authApiMocks.browserLogout.mockReset();
  authApiMocks.registerUser.mockReset();
  authApiMocks.fetchCurrentUser.mockReset();

  if (options?.restoreSession) {
    authApiMocks.browserRefresh.mockResolvedValue(mockTokens);
    authApiMocks.fetchCurrentUser.mockResolvedValue(mockUser);
  } else {
    authApiMocks.browserRefresh.mockRejectedValue(new Error('no session'));
  }

  authApiMocks.browserLogout.mockResolvedValue(undefined);
  authApiMocks.browserLogin.mockResolvedValue(mockTokens);
  authApiMocks.registerUser.mockResolvedValue(mockUser);
}
