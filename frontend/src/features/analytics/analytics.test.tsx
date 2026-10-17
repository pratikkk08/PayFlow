import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mapDailyChartRows } from '@/features/analytics/chartData';
import { analyticsKeys } from '@/features/analytics/analyticsKeys';
import { formatCaptureRate, formatInrMinor } from '@/features/analytics/money';
import { merchantKeys } from '@/features/merchants/merchantKeys';
import {
  inclusiveDayCount,
  resolvePresetRange,
  utcToday,
  validateReportingRange,
} from '@/features/analytics/reportingRange';
import { ApiClientError } from '@/lib/apiClient';
import { clearAccessToken } from '@/lib/authTokenStore';
import { authApiMocks, mockUser, resetAuthApiMocks } from '@/test/authMocks';
import {
  analyticsApiMocks,
  buildSampleDaily,
  resetAnalyticsApiMocks,
} from '@/test/analyticsMocks';
import {
  merchantApiMocks,
  mockMerchants,
  resetMerchantApiMocks,
} from '@/test/merchantMocks';
import { createTestQueryClient, renderAuthenticatedApp } from '@/test/renderApp';
import { mockViewport } from '@/test/viewport';
import { clearStoredMerchantId } from '@/features/merchants/merchantSelectionStorage';

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

describe('money and reporting helpers', () => {
  it('formats INR minor units', () => {
    expect(formatInrMinor(125_050)).toMatch(/1,250\.50/);
    expect(formatInrMinor(0)).toMatch(/0\.00/);
    expect(formatInrMinor(-5_000)).toMatch(/-₹50\.00|−₹50\.00|₹-50\.00/);
  });

  it('formats capture rate and zero denominator as em dash', () => {
    expect(formatCaptureRate(0.6)).toBe('60.0%');
    expect(formatCaptureRate(null)).toBe('—');
    expect(formatCaptureRate(undefined)).toBe('—');
  });

  it('resolves UTC preset ranges inclusively', () => {
    const today = '2026-10-17';
    expect(resolvePresetRange('7d', today)).toEqual({ from: '2026-10-11', to: today });
    expect(resolvePresetRange('30d', today)).toEqual({ from: '2026-09-18', to: today });
    expect(resolvePresetRange('90d', today)).toEqual({ from: '2026-07-20', to: today });
    expect(inclusiveDayCount('2026-10-11', '2026-10-17')).toBe(7);
    expect(utcToday(new Date('2026-10-17T15:30:00Z'))).toBe('2026-10-17');
  });

  it('rejects invalid and overlong custom ranges', () => {
    expect(validateReportingRange('2026-10-17', '2026-10-01')).toMatch(/on or before/i);
    expect(validateReportingRange('2025-01-01', '2026-12-31')).toMatch(/366/i);
    expect(validateReportingRange('not-a-date', '2026-10-17')).toMatch(/valid/i);
    expect(validateReportingRange('2026-10-01', '2026-10-17')).toBeNull();
  });

  it('maps daily chart rows from API points', () => {
    const daily = buildSampleDaily(mockMerchants[0].id, '2026-10-01', '2026-10-02');
    const rows = mapDailyChartRows(daily.days, 'capturedAmountMinor');
    expect(rows).toEqual([
      { date: '2026-10-01', label: 'Oct 1', value: 75_000 },
      { date: '2026-10-02', label: 'Oct 2', value: 50_050 },
    ]);
  });
});

describe('analytics dashboard UI', () => {
  beforeEach(() => {
    mockViewport(1280);
    clearAccessToken();
    clearStoredMerchantId(mockUser.id);
    localStorage.clear();
    resetAuthApiMocks({ restoreSession: true });
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'sample' });
  });

  it('renders overview metrics from the analytics API', async () => {
    renderAuthenticatedApp('/dashboard');

    expect(await screen.findByTestId('analytics-metric-cards')).toBeInTheDocument();
    expect(within(screen.getByTestId('metric-total-payments')).getByText('10')).toBeInTheDocument();
    expect(screen.getByTestId('metric-captured-amount')).toHaveTextContent(/1,250\.50/);
    expect(screen.getByTestId('metric-refund-amount')).toHaveTextContent(/50\.00/);
    expect(screen.getByTestId('metric-net-captured')).toHaveTextContent(/1,200\.50/);
    expect(screen.getByTestId('metric-capture-rate')).toHaveTextContent('60.0%');
    expect(within(screen.getByTestId('metric-failed-payments')).getByText('2')).toBeInTheDocument();
    expect(within(screen.getByTestId('metric-risk-block')).getByText('3')).toBeInTheDocument();
    expect(within(screen.getByTestId('metric-risk-review')).getByText('1')).toBeInTheDocument();
    expect(screen.getByTestId('analytics-disclaimer')).toHaveTextContent(/sandbox activity/i);
    expect(screen.getByTestId('chart-captured-amount')).toBeInTheDocument();
  });

  it('shows empty analytics onboarding when the API reports no activity', async () => {
    resetAnalyticsApiMocks({ mode: 'empty' });
    renderAuthenticatedApp('/dashboard');

    expect(await screen.findByText(/no simulated activity yet/i)).toBeInTheDocument();
    expect(screen.getByTestId('metric-capture-rate')).toHaveTextContent('—');
    expect(screen.queryByTestId('analytics-charts')).not.toBeInTheDocument();
  });

  it('surfaces analytics errors with retry', async () => {
    const user = userEvent.setup();
    analyticsApiMocks.fetchAnalyticsOverview.mockRejectedValue(
      new ApiClientError(500, 'server_error', 'Analytics unavailable'),
    );
    analyticsApiMocks.fetchAnalyticsDaily.mockRejectedValue(
      new ApiClientError(500, 'server_error', 'Analytics unavailable'),
    );
    renderAuthenticatedApp('/dashboard');

    expect(await screen.findByTestId('error-state')).toHaveTextContent(/analytics unavailable/i);
    resetAnalyticsApiMocks({ mode: 'sample' });
    await user.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByTestId('analytics-metric-cards')).toBeInTheDocument();
  });

  it('rejects inverted custom date ranges client-side', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/dashboard');
    await screen.findByTestId('analytics-metric-cards');

    await user.click(screen.getByRole('button', { name: /custom range/i }));
    const fromInput = screen.getByLabelText(/from \(utc\)/i);
    const toInput = screen.getByLabelText(/to \(utc\)/i);
    await user.clear(fromInput);
    await user.clear(toInput);
    fromInput.focus();
    await user.paste('2026-10-17');
    toInput.focus();
    await user.paste('2026-10-01');
    await user.click(screen.getByRole('button', { name: /^apply$/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(/on or before/i);
  });

  it('switches merchants and scopes analytics requests', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/dashboard');
    await screen.findByTestId('analytics-metric-cards');

    await waitFor(() => {
      expect(analyticsApiMocks.fetchAnalyticsOverview).toHaveBeenCalledWith(
        mockMerchants[0].id,
        expect.objectContaining({ from: expect.any(String), to: expect.any(String) }),
      );
    });

    analyticsApiMocks.fetchAnalyticsOverview.mockClear();
    analyticsApiMocks.fetchAnalyticsDaily.mockClear();

    await user.click(screen.getByRole('button', { name: /merchant selector/i }));
    await user.click(screen.getByRole('option', { name: /beta labs/i }));

    await waitFor(() => {
      expect(analyticsApiMocks.fetchAnalyticsOverview).toHaveBeenCalledWith(
        mockMerchants[1].id,
        expect.objectContaining({ from: expect.any(String), to: expect.any(String) }),
      );
    });
    expect(
      within(screen.getByTestId('selected-merchant-summary')).getByText('Beta Labs'),
    ).toBeInTheDocument();
  });

  it('isolates analytics query caches when switching merchants', async () => {
    const user = userEvent.setup();
    const queryClient = createTestQueryClient();
    const range = resolvePresetRange('30d');
    renderAuthenticatedApp('/dashboard', { queryClient });
    await screen.findByTestId('analytics-metric-cards');

    const probeA = [...merchantKeys.scoped(mockMerchants[0].id), 'analytics', 'probe'] as const;
    const probeB = [...merchantKeys.scoped(mockMerchants[1].id), 'analytics', 'probe'] as const;
    queryClient.setQueryData(probeA, { leak: 'acme' });
    queryClient.setQueryData(probeB, { leak: 'beta' });
    expect(queryClient.getQueryData(analyticsKeys.overview(mockMerchants[0].id, range.from, range.to))).toBeDefined();

    await user.click(screen.getByRole('button', { name: /merchant selector/i }));
    await user.click(screen.getByRole('option', { name: /beta labs/i }));

    await waitFor(() => {
      expect(queryClient.getQueryData(probeA)).toBeUndefined();
      expect(queryClient.getQueryData(probeB)).toBeUndefined();
      expect(
        queryClient.getQueryData(analyticsKeys.overview(mockMerchants[0].id, range.from, range.to)),
      ).toBeUndefined();
    });
  });

  it('handles merchant access revocation on analytics', async () => {
    analyticsApiMocks.fetchAnalyticsOverview.mockRejectedValue(
      new ApiClientError(403, 'forbidden', 'Forbidden'),
    );
    analyticsApiMocks.fetchAnalyticsDaily.mockRejectedValue(
      new ApiClientError(403, 'forbidden', 'Forbidden'),
    );
    renderAuthenticatedApp('/dashboard');
    expect(await screen.findByText(/merchant analytics unavailable/i)).toBeInTheDocument();
  });
});
