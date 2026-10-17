import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DASHBOARD_NAV } from '@/app/navigation';
import { clearAccessToken } from '@/lib/authTokenStore';
import { authApiMocks, resetAuthApiMocks } from '@/test/authMocks';
import { merchantApiMocks, resetMerchantApiMocks } from '@/test/merchantMocks';
import { analyticsApiMocks, resetAnalyticsApiMocks } from '@/test/analyticsMocks';
import { paymentApiMocks, resetPaymentApiMocks } from '@/test/paymentMocks';
import { refundApiMocks, resetRefundApiMocks } from '@/test/refundMocks';
import { apiKeyApiMocks, resetApiKeyApiMocks } from '@/test/apiKeyMocks';
import { webhookApiMocks, resetWebhookApiMocks } from '@/test/webhookMocks';
import { renderAuthenticatedApp } from '@/test/renderApp';
import { mockViewport } from '@/test/viewport';
import {
  clearStoredMerchantId,
  merchantSelectionStorageKey,
} from '@/features/merchants/merchantSelectionStorage';
import { mockUser } from '@/test/authMocks';

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

describe('DashboardLayout', () => {
  beforeEach(() => {
    mockViewport(1280);
    clearAccessToken();
    clearStoredMerchantId(mockUser.id);
    localStorage.removeItem(merchantSelectionStorageKey(mockUser.id));
    resetAuthApiMocks({ restoreSession: true });
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'empty' });
    resetPaymentApiMocks();
    resetRefundApiMocks();
    resetApiKeyApiMocks();
    resetWebhookApiMocks();
  });

  it('renders the shell with sidebar navigation links', async () => {
    renderAuthenticatedApp('/dashboard');

    expect(await screen.findByRole('heading', { name: 'Overview' })).toBeInTheDocument();
    expect(await screen.findByTestId('selected-merchant-summary')).toHaveTextContent(
      'Acme Sandbox',
    );
    const nav = screen.getByRole('navigation', { name: 'Dashboard' });
    for (const item of DASHBOARD_NAV) {
      expect(within(nav).getByRole('link', { name: new RegExp(item.label, 'i') })).toHaveAttribute(
        'href',
        item.to,
      );
    }
    expect(
      screen.getByText(/merchant and payment UIs arrive in later tasks/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
    expect(await screen.findByTestId('analytics-disclaimer')).toHaveTextContent(
      /not.*real revenue/i,
    );
  });

  it('marks the active navigation item for the current route', async () => {
    renderAuthenticatedApp('/payments');

    expect(await screen.findByRole('heading', { name: 'Payments' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /payments/i })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByTestId('payments-table')).toBeInTheDocument();
  });

  it('renders wired settings routes and remaining placeholders', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/payments');
    await screen.findByRole('heading', { name: 'Payments' });

    await user.click(screen.getByRole('link', { name: /^api keys/i }));
    expect(await screen.findByTestId('api-keys-page')).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /^webhooks/i }));
    expect(await screen.findByTestId('webhooks-page')).toBeInTheDocument();

    const placeholders = DASHBOARD_NAV.filter((item) => item.to === '/settings');
    for (const item of placeholders) {
      await user.click(screen.getByRole('link', { name: new RegExp(`^${item.label}`, 'i') }));
      expect(screen.getByRole('heading', { name: item.label })).toBeInTheDocument();
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
      expect(screen.getByText(/not wired yet/i)).toBeInTheDocument();
    }
  });

  it('opens and closes the mobile navigation with keyboard support', async () => {
    mockViewport(375);
    const user = userEvent.setup();
    renderAuthenticatedApp('/dashboard');
    await screen.findByRole('heading', { name: 'Overview' });

    const openButton = screen.getByRole('button', { name: /open navigation menu/i });
    expect(openButton).toHaveAttribute('aria-expanded', 'false');

    await user.click(openButton);
    expect(screen.getByRole('dialog', { name: /navigation menu/i })).toBeInTheDocument();
    expect(openButton).toHaveAttribute('aria-expanded', 'true');

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog', { name: /navigation menu/i })).not.toBeInTheDocument();
    await waitForFocus(/open navigation menu/i);
  });
});

async function waitForFocus(name: RegExp) {
  const { waitFor } = await import('@testing-library/react');
  await waitFor(() => {
    expect(screen.getByRole('button', { name })).toHaveFocus();
  });
}
