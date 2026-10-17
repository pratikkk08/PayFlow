import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError } from '@/lib/apiClient';
import { clearAccessToken } from '@/lib/authTokenStore';
import { merchantKeys } from '@/features/merchants/merchantKeys';
import {
  clearStoredMerchantId,
  merchantSelectionStorageKey,
  readStoredMerchantId,
  resolveMerchantSelection,
  writeStoredMerchantId,
} from '@/features/merchants/merchantSelectionStorage';
import { validateMerchantForm } from '@/features/merchants/validation';
import { authApiMocks, mockUser, resetAuthApiMocks } from '@/test/authMocks';
import {
  merchantApiMocks,
  mockMerchants,
  resetMerchantApiMocks,
} from '@/test/merchantMocks';
import { analyticsApiMocks, resetAnalyticsApiMocks } from '@/test/analyticsMocks';
import { createTestQueryClient, renderAuthenticatedApp } from '@/test/renderApp';
import { mockViewport } from '@/test/viewport';

vi.mock('@/features/auth/authApi', () => ({
  browserLogin: (...args: unknown[]) => authApiMocks.browserLogin(...args),
  browserRefresh: (...args: unknown[]) => authApiMocks.browserRefresh(...args),
  browserLogout: (...args: unknown[]) => authApiMocks.browserLogout(...args),
  registerUser: (...args: unknown[]) => authApiMocks.registerUser(...args),
  fetchCurrentUser: (...args: unknown[]) => authApiMocks.fetchCurrentUser(...args),
}));

vi.mock('@/features/merchants/merchantApi', () => ({
  listMerchants: (...args: unknown[]) => merchantApiMocks.listMerchants(...args),
  getMerchant: (...args: unknown[]) => merchantApiMocks.getMerchant(...args),
  createMerchant: (...args: unknown[]) => merchantApiMocks.createMerchant(...args),
  updateMerchant: (...args: unknown[]) => merchantApiMocks.updateMerchant(...args),
}));

vi.mock('@/features/analytics/analyticsApi', () => ({
  fetchAnalyticsOverview: (...args: unknown[]) =>
    analyticsApiMocks.fetchAnalyticsOverview(...args),
  fetchAnalyticsDaily: (...args: unknown[]) => analyticsApiMocks.fetchAnalyticsDaily(...args),
}));

describe('merchant validation and selection helpers', () => {
  it('validates merchant form fields', () => {
    expect(validateMerchantForm('A', '').name).toBeTruthy();
    expect(validateMerchantForm('Acme', 'x'.repeat(2001)).description).toBeTruthy();
    expect(Object.keys(validateMerchantForm('Acme Payments', 'Sandbox'))).toHaveLength(0);
  });

  it('resolves stale preferred ids to a valid merchant', () => {
    expect(resolveMerchantSelection(mockMerchants, 'missing-id')).toBe(mockMerchants[0].id);
    expect(resolveMerchantSelection(mockMerchants, mockMerchants[1].id)).toBe(mockMerchants[1].id);
    expect(resolveMerchantSelection([], 'anything')).toBeNull();
  });
});

describe('merchants UI', () => {
  beforeEach(() => {
    mockViewport(1280);
    clearAccessToken();
    clearStoredMerchantId(mockUser.id);
    localStorage.clear();
    resetAuthApiMocks({ restoreSession: true });
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'empty' });
  });

  it('loads the merchant list', async () => {
    renderAuthenticatedApp('/merchants');
    expect(await screen.findByTestId('merchant-list')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Acme Sandbox' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Beta Labs' })).toBeInTheDocument();
  });

  it('shows empty merchant list with create action', async () => {
    resetMerchantApiMocks({ merchants: [] });
    renderAuthenticatedApp('/merchants');
    expect(await screen.findByText(/no merchants yet/i)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /create merchant/i }).length).toBeGreaterThan(0);
  });

  it('creates a merchant successfully', async () => {
    const user = userEvent.setup();
    resetMerchantApiMocks({ merchants: [] });
    merchantApiMocks.createMerchant.mockImplementation(async (payload: {
      name: string;
      description?: string | null;
    }) => {
      const created = {
        id: '44444444-4444-4444-4444-444444444444',
        name: payload.name,
        description: payload.description ?? null,
        status: 'ACTIVE' as const,
        ownerUserId: mockUser.id,
        createdAt: '2026-01-04T00:00:00Z',
        updatedAt: '2026-01-04T00:00:00Z',
      };
      merchantApiMocks.listMerchants.mockResolvedValue([created]);
      return created;
    });
    renderAuthenticatedApp('/merchants/new');

    await screen.findByRole('heading', { name: /create merchant/i });
    await user.type(screen.getByLabelText(/merchant name/i), 'New Co');
    await user.type(screen.getByLabelText(/^description$/i), 'Fresh sandbox');
    await user.click(screen.getByRole('button', { name: /^create merchant$/i }));

    await waitFor(() => {
      expect(merchantApiMocks.createMerchant).toHaveBeenCalledWith({
        name: 'New Co',
        description: 'Fresh sandbox',
      });
    });
    expect(await screen.findByTestId('merchant-list')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'New Co' })).toBeInTheDocument();
  });

  it('surfaces create merchant API failures', async () => {
    const user = userEvent.setup();
    merchantApiMocks.createMerchant.mockRejectedValue(
      new ApiClientError(400, 'validation_error', 'Name is invalid'),
    );
    renderAuthenticatedApp('/merchants/new');
    await screen.findByRole('heading', { name: /create merchant/i });
    await user.type(screen.getByLabelText(/merchant name/i), 'Broken Co');
    await user.click(screen.getByRole('button', { name: /^create merchant$/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/name is invalid/i);
  });

  it('edits a merchant name and description', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp(`/merchants/${mockMerchants[0].id}/edit`);

    await screen.findByRole('heading', { name: /edit merchant/i });
    const nameInput = screen.getByLabelText(/merchant name/i);
    await user.clear(nameInput);
    await user.type(nameInput, 'Acme Renamed');
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(merchantApiMocks.updateMerchant).toHaveBeenCalledWith(mockMerchants[0].id, {
        name: 'Acme Renamed',
        description: 'Primary test merchant',
      });
    });
  });

  it('switches merchants from the selector', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/dashboard');
    await screen.findByTestId('selected-merchant-summary');
    expect(within(screen.getByTestId('selected-merchant-summary')).getByText('Acme Sandbox')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /merchant selector/i }));
    await user.click(screen.getByRole('option', { name: /beta labs/i }));

    await waitFor(() => {
      expect(readStoredMerchantId(mockUser.id)).toBe(mockMerchants[1].id);
    });
    expect(
      within(screen.getByTestId('selected-merchant-summary')).getByText('Beta Labs'),
    ).toBeInTheDocument();
  });

  it('ignores a stale stored merchant selection', async () => {
    writeStoredMerchantId(mockUser.id, '00000000-0000-0000-0000-000000000000');
    renderAuthenticatedApp('/dashboard');
    await screen.findByTestId('selected-merchant-summary');
    expect(within(screen.getByTestId('selected-merchant-summary')).getByText('Acme Sandbox')).toBeInTheDocument();
    expect(readStoredMerchantId(mockUser.id)).toBe(mockMerchants[0].id);
  });

  it('clears merchant preference and query cache on logout', async () => {
    const user = userEvent.setup();
    writeStoredMerchantId(mockUser.id, mockMerchants[0].id);
    const { queryClient } = renderAuthenticatedApp('/dashboard');
    await screen.findByTestId('selected-merchant-summary');

    queryClient.setQueryData(merchantKeys.scoped(mockMerchants[0].id), { leak: true });

    await user.click(screen.getByRole('button', { name: /account menu for test operator/i }));
    await user.click(screen.getByRole('menuitem', { name: /sign out/i }));

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    expect(localStorage.getItem(merchantSelectionStorageKey(mockUser.id))).toBeNull();
    expect(queryClient.getQueryData(merchantKeys.scoped(mockMerchants[0].id))).toBeUndefined();
  });

  it('isolates scoped query caches when switching merchants', async () => {
    const user = userEvent.setup();
    const queryClient = createTestQueryClient();
    renderAuthenticatedApp('/dashboard', { queryClient });
    await screen.findByTestId('selected-merchant-summary');

    queryClient.setQueryData(merchantKeys.scoped(mockMerchants[0].id), { from: 'acme' });
    queryClient.setQueryData(merchantKeys.scoped(mockMerchants[1].id), { from: 'beta' });

    await user.click(screen.getByRole('button', { name: /merchant selector/i }));
    await user.click(screen.getByRole('option', { name: /beta labs/i }));

    await waitFor(() => {
      expect(queryClient.getQueryData(merchantKeys.scoped(mockMerchants[0].id))).toBeUndefined();
      expect(queryClient.getQueryData(merchantKeys.scoped(mockMerchants[1].id))).toBeUndefined();
    });
  });

  it('handles list 401/403/404 style API errors', async () => {
    merchantApiMocks.listMerchants.mockRejectedValue(
      new ApiClientError(403, 'forbidden', 'Forbidden'),
    );
    renderAuthenticatedApp('/merchants');
    expect(await screen.findByTestId('error-state')).toBeInTheDocument();
    expect(screen.getByText(/forbidden/i)).toBeInTheDocument();
  });
});
