import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '@/app/App';
import { clearAccessToken } from '@/lib/authTokenStore';
import { authApiMocks, resetAuthApiMocks } from '@/test/authMocks';
import { resetMerchantApiMocks, merchantApiMocks } from '@/test/merchantMocks';
import { analyticsApiMocks, resetAnalyticsApiMocks } from '@/test/analyticsMocks';

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

describe('App', () => {
  beforeEach(() => {
    clearAccessToken();
    resetAuthApiMocks();
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'empty' });
  });

  it('renders the PayFlow home shell', async () => {
    render(<App />);
    expect(
      await screen.findByRole('heading', { name: /simulated merchant payments/i }),
    ).toBeInTheDocument();
    expect(screen.getByText('PayFlow')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('link', { name: /sign in/i })).toBeInTheDocument();
    });
  });
});
