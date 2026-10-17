import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError } from '@/lib/apiClient';
import { clearAccessToken } from '@/lib/authTokenStore';
import { apiKeyKeys } from '@/features/settings/apiKeyKeys';
import { validateCreateApiKeyForm } from '@/features/settings/validation';
import { authApiMocks, mockUser, resetAuthApiMocks } from '@/test/authMocks';
import { analyticsApiMocks, resetAnalyticsApiMocks } from '@/test/analyticsMocks';
import {
  apiKeyApiMocks,
  mockApiKeys,
  resetApiKeyApiMocks,
} from '@/test/apiKeyMocks';
import {
  merchantApiMocks,
  mockMerchants,
  resetMerchantApiMocks,
} from '@/test/merchantMocks';
import { paymentApiMocks, resetPaymentApiMocks } from '@/test/paymentMocks';
import { refundApiMocks, resetRefundApiMocks } from '@/test/refundMocks';
import { webhookApiMocks, resetWebhookApiMocks } from '@/test/webhookMocks';
import { createTestQueryClient, renderAuthenticatedApp } from '@/test/renderApp';
import { mockViewport } from '@/test/viewport';
import { clearStoredMerchantId } from '@/features/merchants/merchantSelectionStorage';
import { copyTextToClipboard } from '@/lib/clipboard';

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

vi.mock('@/features/payments/paymentApi', () => ({
  createPayment: (...args: unknown[]) => paymentApiMocks.createPayment(...args),
  listPayments: (...args: unknown[]) => paymentApiMocks.listPayments(...args),
  getPayment: (...args: unknown[]) => paymentApiMocks.getPayment(...args),
  authorizePayment: (...args: unknown[]) => paymentApiMocks.authorizePayment(...args),
  capturePayment: (...args: unknown[]) => paymentApiMocks.capturePayment(...args),
  cancelPayment: (...args: unknown[]) => paymentApiMocks.cancelPayment(...args),
}));

vi.mock('@/features/refunds/refundApi', () => ({
  createRefund: (...args: unknown[]) => refundApiMocks.createRefund(...args),
  listRefundsForPayment: (...args: unknown[]) =>
    refundApiMocks.listRefundsForPayment(...args),
  getRefund: (...args: unknown[]) => refundApiMocks.getRefund(...args),
}));

vi.mock('@/features/settings/apiKeyApi', () => ({
  listApiKeys: (...args: unknown[]) => apiKeyApiMocks.listApiKeys(...args),
  createApiKey: (...args: unknown[]) => apiKeyApiMocks.createApiKey(...args),
  revokeApiKey: (...args: unknown[]) => apiKeyApiMocks.revokeApiKey(...args),
}));

vi.mock('@/features/webhooks/webhookApi', () => ({
  listWebhooks: (...args: unknown[]) => webhookApiMocks.listWebhooks(...args),
  createWebhook: (...args: unknown[]) => webhookApiMocks.createWebhook(...args),
  updateWebhook: (...args: unknown[]) => webhookApiMocks.updateWebhook(...args),
  deleteWebhook: (...args: unknown[]) => webhookApiMocks.deleteWebhook(...args),
}));

vi.mock('@/lib/clipboard', () => ({
  copyTextToClipboard: vi.fn().mockResolvedValue(undefined),
}));

describe('api key helpers', () => {
  it('validates create form name length', () => {
    expect(validateCreateApiKeyForm({ name: 'a', type: 'SECRET' }).ok).toBe(false);
    expect(validateCreateApiKeyForm({ name: 'Ops key', type: 'PUBLISHABLE' })).toEqual({
      ok: true,
      name: 'Ops key',
      type: 'PUBLISHABLE',
    });
  });
});

describe('API keys UI', () => {
  beforeEach(() => {
    mockViewport(1280);
    clearAccessToken();
    clearStoredMerchantId(mockUser.id);
    localStorage.clear();
    sessionStorage.clear();
    resetAuthApiMocks({ restoreSession: true });
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'empty' });
    resetPaymentApiMocks();
    resetRefundApiMocks();
    resetApiKeyApiMocks();
    resetWebhookApiMocks();
    vi.mocked(copyTextToClipboard).mockReset();
    vi.mocked(copyTextToClipboard).mockResolvedValue(undefined);
  });

  it('lists API keys with active and revoked status', async () => {
    renderAuthenticatedApp('/api-keys');
    const table = await screen.findByTestId('api-keys-table');
    expect(within(table).getByText('Primary secret')).toBeInTheDocument();
    expect(within(table).getByText('Old publishable')).toBeInTheDocument();
    expect(within(table).getByText('Active')).toBeInTheDocument();
    expect(within(table).getByText('Revoked')).toBeInTheDocument();
    expect(within(table).getByText(/sk_test_abc12345/)).toBeInTheDocument();
    expect(screen.queryByText(/ONCE_ONLY/)).not.toBeInTheDocument();
  });

  it('shows empty state when merchant has no keys', async () => {
    resetApiKeyApiMocks({ keys: [] });
    renderAuthenticatedApp('/api-keys');
    expect(await screen.findByText(/no api keys yet/i)).toBeInTheDocument();
  });

  it('creates a key, reveals secret once, copies, then clears on close', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderAuthenticatedApp('/api-keys');
    await screen.findByTestId('api-keys-page');

    await user.type(screen.getByLabelText(/key name/i), 'CI secret');
    await user.click(screen.getByTestId('create-api-key'));

    expect(await screen.findByTestId('secret-reveal-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('revealed-secret')).toHaveValue(
      'sk_test_newkey12_ONCE_ONLY_SECRET_VALUE',
    );
    expect(apiKeyApiMocks.createApiKey).toHaveBeenCalledWith(mockMerchants[0].id, {
      name: 'CI secret',
      type: 'SECRET',
    });

    // Secret must not land in the list query cache.
    const cached = queryClient.getQueryData(apiKeyKeys.list(mockMerchants[0].id));
    expect(JSON.stringify(cached ?? [])).not.toContain('ONCE_ONLY');

    await user.click(screen.getByTestId('copy-secret'));
    await waitFor(() => {
      expect(copyTextToClipboard).toHaveBeenCalledWith(
        'sk_test_newkey12_ONCE_ONLY_SECRET_VALUE',
      );
    });
    expect(screen.getByRole('button', { name: /^copied$/i })).toBeInTheDocument();

    await user.click(screen.getByTestId('close-secret-dialog'));
    await waitFor(() => {
      expect(screen.queryByTestId('secret-reveal-dialog')).not.toBeInTheDocument();
    });
    expect(screen.queryByDisplayValue(/ONCE_ONLY/)).not.toBeInTheDocument();
    expect(localStorage.getItem('CI secret')).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('clears revealed secret when switching merchants', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/api-keys');
    await screen.findByTestId('api-keys-page');

    await user.type(screen.getByLabelText(/key name/i), 'Temp');
    await user.click(screen.getByTestId('create-api-key'));
    expect(await screen.findByTestId('revealed-secret')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /merchant selector/i }));
    await user.click(screen.getByRole('option', { name: /beta labs/i }));

    await waitFor(() => {
      expect(screen.queryByTestId('secret-reveal-dialog')).not.toBeInTheDocument();
    });
    expect(apiKeyApiMocks.listApiKeys).toHaveBeenCalledWith(mockMerchants[1].id);
  });

  it('confirms revocation', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/api-keys');
    await screen.findByTestId('api-keys-table');

    await user.click(screen.getByTestId(`revoke-api-key-${mockApiKeys[0].id}`));
    expect(screen.getByRole('alertdialog')).toHaveTextContent(/revoke api key/i);
    await user.click(screen.getByRole('button', { name: /revoke key/i }));

    await waitFor(() => {
      expect(apiKeyApiMocks.revokeApiKey).toHaveBeenCalledWith(
        mockMerchants[0].id,
        mockApiKeys[0].id,
      );
    });
  });

  it('surfaces create errors', async () => {
    const user = userEvent.setup();
    apiKeyApiMocks.createApiKey.mockRejectedValueOnce(
      new ApiClientError(400, 'validation_error', 'Name is required'),
    );
    renderAuthenticatedApp('/api-keys');
    await screen.findByTestId('api-keys-page');

    await user.type(screen.getByLabelText(/key name/i), 'Broken');
    await user.click(screen.getByTestId('create-api-key'));

    expect(await screen.findByTestId('api-key-form-error')).toHaveTextContent(/name is required/i);
    expect(screen.queryByTestId('secret-reveal-dialog')).not.toBeInTheDocument();
  });

  it('scopes query keys by merchant id', () => {
    const client = createTestQueryClient();
    expect(apiKeyKeys.list(mockMerchants[0].id)).not.toEqual(
      apiKeyKeys.list(mockMerchants[1].id),
    );
    client.setQueryData(apiKeyKeys.list(mockMerchants[0].id), mockApiKeys);
    expect(client.getQueryData(apiKeyKeys.list(mockMerchants[1].id))).toBeUndefined();
  });
});
