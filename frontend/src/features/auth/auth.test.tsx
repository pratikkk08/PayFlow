import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import { validateLogin, validateRegistration } from '@/features/auth/validation';
import { ApiClientError, createApiClient, __resetRefreshInFlightForTests } from '@/lib/apiClient';
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from '@/lib/authTokenStore';
import { authApiMocks, mockTokens, mockUser, resetAuthApiMocks } from '@/test/authMocks';
import { merchantApiMocks, resetMerchantApiMocks } from '@/test/merchantMocks';
import { analyticsApiMocks, resetAnalyticsApiMocks } from '@/test/analyticsMocks';
import { paymentApiMocks, resetPaymentApiMocks } from '@/test/paymentMocks';
import { refundApiMocks, resetRefundApiMocks } from '@/test/refundMocks';
import { renderAuthenticatedApp } from '@/test/renderApp';

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

vi.mock('@/lib/csrf', () => ({
  csrfHeaders: () => ({ 'X-XSRF-TOKEN': 'test-csrf' }),
  readCsrfToken: () => 'test-csrf',
}));

function renderApp(path: string) {
  return renderAuthenticatedApp(path);
}

describe('auth validation', () => {
  it('validates login fields', () => {
    expect(validateLogin('', '')).toMatchObject({
      email: expect.any(String),
      password: expect.any(String),
    });
    expect(validateLogin('bad', 'x')).toHaveProperty('email');
    expect(Object.keys(validateLogin('a@b.co', 'secret'))).toHaveLength(0);
  });

  it('validates registration against backend constraints', () => {
    expect(validateRegistration('A', 'a@b.co', 'short').fullName).toBeTruthy();
    expect(validateRegistration('Ada Lovelace', 'a@b.co', 'short').password).toBeTruthy();
    expect(validateRegistration('Ada Lovelace', 'a@b.co', 'alllowercase1').password).toBeTruthy();
    expect(
      Object.keys(validateRegistration('Ada Lovelace', 'a@b.co', 'Str0ngPass!')),
    ).toHaveLength(0);
  });
});

describe('login and registration UI', () => {
  beforeEach(() => {
    clearAccessToken();
    resetAuthApiMocks();
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'empty' });
    resetPaymentApiMocks();
    resetRefundApiMocks();
  });

  it('logs in successfully and lands on the dashboard', async () => {
    const user = userEvent.setup();
    authApiMocks.browserLogin.mockResolvedValue(mockTokens);
    authApiMocks.fetchCurrentUser.mockResolvedValue(mockUser);

    renderApp('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    await user.type(screen.getByLabelText(/^email$/i), 'operator@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'Str0ngPass!');
    await user.click(screen.getByRole('button', { name: /^sign in$/i }));

    await waitFor(() => {
      expect(authApiMocks.browserLogin).toHaveBeenCalledWith({
        email: 'operator@example.com',
        password: 'Str0ngPass!',
      });
    });
    expect(await screen.findByRole('heading', { name: 'Overview' })).toBeInTheDocument();
    expect(screen.getByText('Test Operator')).toBeInTheDocument();
  });

  it('shows server error on failed login', async () => {
    const user = userEvent.setup();
    authApiMocks.browserLogin.mockRejectedValue(
      new ApiClientError(401, 'invalid_credentials', 'Invalid email or password'),
    );

    renderApp('/login');
    await screen.findByRole('heading', { name: /sign in/i });
    await user.type(screen.getByLabelText(/^email$/i), 'operator@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'WrongPass1!');
    await user.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid email or password/i);
    expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });

  it('blocks weak registration passwords client-side', async () => {
    const user = userEvent.setup();
    renderApp('/register');
    await screen.findByRole('heading', { name: /create account/i });

    await user.type(screen.getByLabelText(/full name/i), 'Ada Lovelace');
    await user.type(screen.getByLabelText(/^email$/i), 'ada@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'weak');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/password must be 10/i)).toBeInTheDocument();
    expect(authApiMocks.registerUser).not.toHaveBeenCalled();
  });

  it('handles duplicate email on registration', async () => {
    const user = userEvent.setup();
    authApiMocks.registerUser.mockRejectedValue(
      new ApiClientError(409, 'email_taken', 'Email is already registered'),
    );

    renderApp('/register');
    await screen.findByRole('heading', { name: /create account/i });
    await user.type(screen.getByLabelText(/full name/i), 'Ada Lovelace');
    await user.type(screen.getByLabelText(/^email$/i), 'ada@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'Str0ngPass!');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/already exists/i);
  });
});

describe('protected routes and logout', () => {
  beforeEach(() => {
    clearAccessToken();
    resetAuthApiMocks();
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'empty' });
    resetPaymentApiMocks();
    resetRefundApiMocks();
  });

  it('redirects unauthenticated visitors to login and preserves destination', async () => {
    renderApp('/payments');
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();

    const user = userEvent.setup();
    authApiMocks.browserLogin.mockResolvedValue(mockTokens);
    authApiMocks.fetchCurrentUser.mockResolvedValue(mockUser);
    await user.type(screen.getByLabelText(/^email$/i), 'operator@example.com');
    await user.type(screen.getByLabelText(/^password$/i), 'Str0ngPass!');
    await user.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(await screen.findByRole('heading', { name: 'Payments' })).toBeInTheDocument();
    expect(await screen.findByTestId('payments-table')).toBeInTheDocument();
  });

  it('allows authenticated dashboard access after session restore', async () => {
    resetAuthApiMocks({ restoreSession: true });
    renderApp('/dashboard');
    expect(await screen.findByRole('heading', { name: 'Overview' })).toBeInTheDocument();
    expect(screen.getByText('Test Operator')).toBeInTheDocument();
  });

  it('logs out and returns to login', async () => {
    resetAuthApiMocks({ restoreSession: true });
    const user = userEvent.setup();
    renderApp('/dashboard');
    expect(await screen.findByText('Test Operator')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /account menu for test operator/i }));
    await user.click(screen.getByRole('menuitem', { name: /sign out/i }));

    await waitFor(() => {
      expect(authApiMocks.browserLogout).toHaveBeenCalled();
    });
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    expect(getAccessToken()).toBeNull();
  });
});

describe('access token refresh coordination', () => {
  beforeEach(() => {
    clearAccessToken();
    __resetRefreshInFlightForTests();
    resetAuthApiMocks();
    vi.restoreAllMocks();
  });

  it('refreshes once for concurrent 401s and retries original requests', async () => {
    setAccessToken('expired-token');
    authApiMocks.browserRefresh.mockImplementation(async () => {
      await new Promise((r) => setTimeout(r, 20));
      return { ...mockTokens, accessToken: 'rotated-token' };
    });

    const adapter = vi.fn(async (config: { headers: Record<string, unknown>; url?: string }) => {
      const auth = String(config.headers.Authorization ?? '');
      if (auth.includes('expired-token')) {
        const error = new axios.AxiosError('Unauthorized');
        error.config = config as never;
        error.response = {
          status: 401,
          data: { error: 'unauthorized', message: 'expired' },
          statusText: 'Unauthorized',
          headers: {},
          config: config as never,
        };
        throw error;
      }
      return {
        data: { ok: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      };
    });

    const client = createApiClient('');
    client.defaults.adapter = adapter as never;

    const [a, b] = await Promise.all([
      client.get('/api/v1/users/me'),
      client.get('/api/v1/users/me'),
    ]);

    expect(a.data).toEqual({ ok: true });
    expect(b.data).toEqual({ ok: true });
    expect(authApiMocks.browserRefresh).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBe('rotated-token');
  });

  it('clears session when refresh fails', async () => {
    setAccessToken('expired-token');
    authApiMocks.browserRefresh.mockRejectedValue(
      new ApiClientError(401, 'invalid_refresh_token', 'Missing refresh session'),
    );

    const adapter = vi.fn(async (config: { headers: Record<string, unknown> }) => {
      const error = new axios.AxiosError('Unauthorized');
      error.config = config as never;
      error.response = {
        status: 401,
        data: { error: 'unauthorized', message: 'expired' },
        statusText: 'Unauthorized',
        headers: {},
        config: config as never,
      };
      throw error;
    });

    const client = createApiClient('');
    client.defaults.adapter = adapter as never;

    await expect(client.get('/api/v1/users/me')).rejects.toBeInstanceOf(ApiClientError);
    expect(getAccessToken()).toBeNull();
  });
});

describe('API error mapping', () => {
  it('surfaces structured API errors from the client', async () => {
    const adapter = vi.fn(async (config: object) => {
      const error = new axios.AxiosError('Bad Request');
      error.config = config as never;
      error.response = {
        status: 400,
        data: { error: 'validation_error', message: 'Bad input', path: '/api' },
        statusText: 'Bad Request',
        headers: {},
        config: config as never,
      };
      throw error;
    });
    const client = createApiClient('');
    client.defaults.adapter = adapter as never;

    try {
      await client.post('/api/v1/auth/register', {});
      expect.unreachable();
    } catch (error) {
      expect(error).toBeInstanceOf(ApiClientError);
      expect(error).toMatchObject({
        status: 400,
        code: 'validation_error',
        message: 'Bad input',
      });
    }
  });
});

describe('public routes stay public', () => {
  beforeEach(() => {
    clearAccessToken();
    resetAuthApiMocks();
  });

  it('keeps home reachable without a session', async () => {
    renderApp('/');
    expect(
      await screen.findByRole('heading', { name: /simulated merchant payments/i }),
    ).toBeInTheDocument();
  });
});
