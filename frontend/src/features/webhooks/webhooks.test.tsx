import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError } from '@/lib/apiClient';
import { clearAccessToken } from '@/lib/authTokenStore';
import { validateWebhookForm, validateWebhookUrl } from '@/features/webhooks/validation';
import { webhookKeys } from '@/features/webhooks/webhookKeys';
import { authApiMocks, mockUser, resetAuthApiMocks } from '@/test/authMocks';
import { analyticsApiMocks, resetAnalyticsApiMocks } from '@/test/analyticsMocks';
import { apiKeyApiMocks, resetApiKeyApiMocks } from '@/test/apiKeyMocks';
import {
  merchantApiMocks,
  mockMerchants,
  resetMerchantApiMocks,
} from '@/test/merchantMocks';
import { paymentApiMocks, resetPaymentApiMocks } from '@/test/paymentMocks';
import { refundApiMocks, resetRefundApiMocks } from '@/test/refundMocks';
import {
  mockWebhooks,
  resetWebhookApiMocks,
  webhookApiMocks,
} from '@/test/webhookMocks';
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

describe('webhook helpers', () => {
  it('accepts public HTTPS URLs and rejects unsafe destinations', () => {
    expect(validateWebhookUrl('https://hooks.example.com/payflow').ok).toBe(true);
    expect(validateWebhookUrl('http://hooks.example.com/payflow').ok).toBe(false);
    expect(validateWebhookUrl('https://localhost/hooks').ok).toBe(false);
    expect(validateWebhookUrl('https://127.0.0.1/hooks').ok).toBe(false);
    expect(validateWebhookUrl('https://192.168.1.10/hooks').ok).toBe(false);
    expect(validateWebhookUrl('https://user:pass@hooks.example.com/x').ok).toBe(false);
    expect(validateWebhookUrl('https://hooks.example.com/x#frag').ok).toBe(false);
  });

  it('requires at least one supported event type', () => {
    expect(
      validateWebhookForm({
        url: 'https://hooks.example.com/x',
        eventTypes: [],
        enabled: true,
      }).ok,
    ).toBe(false);
    expect(
      validateWebhookForm({
        url: 'https://hooks.example.com/x',
        eventTypes: ['payment.captured'],
        enabled: true,
      }).ok,
    ).toBe(true);
  });
});

describe('webhooks UI', () => {
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

  it('lists endpoints and empty state', async () => {
    renderAuthenticatedApp('/webhooks');
    expect(await screen.findByTestId('webhooks-list')).toBeInTheDocument();
    expect(screen.getByText('https://hooks.example.com/payflow')).toBeInTheDocument();
    expect(screen.getByText('Enabled')).toBeInTheDocument();

    resetWebhookApiMocks({ webhooks: [] });
    renderAuthenticatedApp('/webhooks');
    expect(await screen.findByText(/no webhook endpoints/i)).toBeInTheDocument();
  });

  it('creates endpoint, reveals signing secret once, clears on close', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderAuthenticatedApp('/webhooks');
    await screen.findByTestId('webhooks-page');

    const urlInput = screen.getByLabelText(/https url/i);
    await user.clear(urlInput);
    await user.type(urlInput, 'https://receiver.example.org/hooks');
    await user.click(screen.getByTestId('create-webhook'));

    expect(await screen.findByTestId('secret-reveal-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('revealed-secret')).toHaveValue(
      'whsec_newsecret1_ONCE_ONLY_SIGNING_SECRET',
    );
    expect(apiKeyApiMocks.createApiKey).not.toHaveBeenCalled();
    expect(webhookApiMocks.createWebhook).toHaveBeenCalled();

    const cached = queryClient.getQueryData(webhookKeys.list(mockMerchants[0].id));
    expect(JSON.stringify(cached ?? [])).not.toContain('ONCE_ONLY');

    await user.click(screen.getByTestId('copy-secret'));
    await waitFor(() => {
      expect(copyTextToClipboard).toHaveBeenCalledWith(
        'whsec_newsecret1_ONCE_ONLY_SIGNING_SECRET',
      );
    });
    expect(screen.getByRole('button', { name: /^copied$/i })).toBeInTheDocument();

    await user.click(screen.getByTestId('close-secret-dialog'));
    await waitFor(() => {
      expect(screen.queryByTestId('secret-reveal-dialog')).not.toBeInTheDocument();
    });
  });

  it('clears signing secret when merchant changes', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/webhooks');
    await screen.findByTestId('webhooks-page');

    await user.clear(screen.getByLabelText(/https url/i));
    await user.type(screen.getByLabelText(/https url/i), 'https://a.example.com/h');
    await user.click(screen.getByTestId('create-webhook'));
    expect(await screen.findByTestId('revealed-secret')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /merchant selector/i }));
    await user.click(screen.getByRole('option', { name: /beta labs/i }));

    await waitFor(() => {
      expect(screen.queryByTestId('secret-reveal-dialog')).not.toBeInTheDocument();
    });
  });

  it('rejects invalid HTTPS URLs client-side', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/webhooks');
    await screen.findByTestId('webhooks-page');

    await user.clear(screen.getByLabelText(/https url/i));
    await user.type(screen.getByLabelText(/https url/i), 'http://insecure.example.com/h');
    await user.click(screen.getByTestId('create-webhook'));

    expect(await screen.findByText(/must use https/i)).toBeInTheDocument();
    expect(webhookApiMocks.createWebhook).not.toHaveBeenCalled();
  });

  it('updates subscriptions and disables an endpoint', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/webhooks');
    const row = await screen.findByTestId(`webhook-row-${mockWebhooks[0].id}`);

    await user.click(within(row).getByTestId(`edit-webhook-${mockWebhooks[0].id}`));
    const form = screen.getByTestId(`edit-webhook-form-${mockWebhooks[0].id}`);
    await user.click(within(form).getByText('payment.failed'));
    await user.click(screen.getByTestId('save-webhook-edit'));

    await waitFor(() => {
      expect(webhookApiMocks.updateWebhook).toHaveBeenCalledWith(
        mockMerchants[0].id,
        mockWebhooks[0].id,
        expect.objectContaining({
          eventTypes: expect.arrayContaining(['payment.failed', 'payment.captured']),
        }),
      );
    });

    await user.click(screen.getByTestId(`toggle-webhook-${mockWebhooks[0].id}`));
    await waitFor(() => {
      expect(webhookApiMocks.updateWebhook).toHaveBeenCalledWith(
        mockMerchants[0].id,
        mockWebhooks[0].id,
        { enabled: false },
      );
    });
  });

  it('deletes with confirmation', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/webhooks');
    await screen.findByTestId(`webhook-row-${mockWebhooks[0].id}`);

    await user.click(screen.getByTestId(`delete-webhook-${mockWebhooks[0].id}`));
    expect(screen.getByRole('alertdialog')).toHaveTextContent(/delete webhook endpoint/i);
    await user.click(screen.getByRole('button', { name: /delete endpoint/i }));

    await waitFor(() => {
      expect(webhookApiMocks.deleteWebhook).toHaveBeenCalledWith(
        mockMerchants[0].id,
        mockWebhooks[0].id,
      );
    });
  });

  it('shows server validation errors', async () => {
    const user = userEvent.setup();
    webhookApiMocks.createWebhook.mockRejectedValueOnce(
      new ApiClientError(409, 'conflict', 'Webhook URL already registered for this merchant'),
    );
    renderAuthenticatedApp('/webhooks');
    await screen.findByTestId('webhooks-page');

    await user.clear(screen.getByLabelText(/https url/i));
    await user.type(screen.getByLabelText(/https url/i), 'https://dup.example.com/h');
    await user.click(screen.getByTestId('create-webhook'));

    expect(await screen.findByTestId('webhook-form-error')).toHaveTextContent(/already registered/i);
  });

  it('scopes query keys by merchant', () => {
    const client = createTestQueryClient();
    client.setQueryData(webhookKeys.list(mockMerchants[0].id), mockWebhooks);
    expect(client.getQueryData(webhookKeys.list(mockMerchants[1].id))).toBeUndefined();
  });
});
