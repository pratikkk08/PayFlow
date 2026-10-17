import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError } from '@/lib/apiClient';
import { clearAccessToken } from '@/lib/authTokenStore';
import { createIdempotencyKey, isUncertainMutationError } from '@/features/payments/idempotency';
import { parseInrMajorToMinor } from '@/features/payments/moneyConvert';
import { paymentKeys } from '@/features/payments/paymentKeys';
import { validateCreatePaymentForm } from '@/features/payments/validation';
import { remainingRefundableMinor } from '@/features/refunds/validation';
import { authApiMocks, mockUser, resetAuthApiMocks } from '@/test/authMocks';
import { analyticsApiMocks, resetAnalyticsApiMocks } from '@/test/analyticsMocks';
import {
  merchantApiMocks,
  mockMerchants,
  resetMerchantApiMocks,
} from '@/test/merchantMocks';
import {
  mockPayments,
  paymentApiMocks,
  resetPaymentApiMocks,
} from '@/test/paymentMocks';
import { refundApiMocks, resetRefundApiMocks } from '@/test/refundMocks';
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

describe('payment helpers', () => {
  it('converts INR major to minor without float rounding', () => {
    expect(parseInrMajorToMinor('10.10')).toEqual({ ok: true, amountMinor: 1010 });
    expect(parseInrMajorToMinor('0.01')).toEqual({ ok: true, amountMinor: 1 });
    expect(parseInrMajorToMinor('1.999').ok).toBe(false);
    expect(parseInrMajorToMinor('0').ok).toBe(false);
    expect(parseInrMajorToMinor('1250.50')).toEqual({ ok: true, amountMinor: 125_050 });
  });

  it('validates create payment fields', () => {
    expect(validateCreatePaymentForm({ merchantReference: '', amount: '10', description: '' }).errors)
      .toHaveProperty('merchantReference');
    expect(
      validateCreatePaymentForm({
        merchantReference: 'ref_1',
        amount: '10.00',
        description: '',
      }).amountMinor,
    ).toBe(1000);
  });

  it('creates valid idempotency keys and classifies uncertain errors', () => {
    const key = createIdempotencyKey();
    expect(key.length).toBeGreaterThanOrEqual(8);
    expect(isUncertainMutationError(new ApiClientError(0, 'timeout', 'timed out'))).toBe(true);
    expect(isUncertainMutationError(new ApiClientError(500, 'server_error', 'boom'))).toBe(true);
    expect(isUncertainMutationError(new ApiClientError(400, 'validation_error', 'bad'))).toBe(
      false,
    );
  });

  it('computes remaining refundable capacity', () => {
    expect(
      remainingRefundableMinor(50_000, [
        { amountMinor: 10_000, status: 'SUCCEEDED' },
        { amountMinor: 5_000, status: 'PENDING' },
        { amountMinor: 1_000, status: 'FAILED' },
      ]),
    ).toBe(35_000);
  });
});

describe('payments UI', () => {
  beforeEach(() => {
    mockViewport(1280);
    clearAccessToken();
    clearStoredMerchantId(mockUser.id);
    localStorage.clear();
    resetAuthApiMocks({ restoreSession: true });
    resetMerchantApiMocks();
    resetAnalyticsApiMocks({ mode: 'empty' });
    resetPaymentApiMocks();
    resetRefundApiMocks();
  });

  it('lists merchant payments with pagination meta', async () => {
    renderAuthenticatedApp('/payments');
    expect(await screen.findByTestId('payments-table')).toBeInTheDocument();
    expect(screen.getByText('order_1001')).toBeInTheDocument();
    expect(screen.getByText('order_1002')).toBeInTheDocument();
    expect(screen.getByTestId('payments-page-meta')).toHaveTextContent(/2 total/i);
  });

  it('shows empty payment list', async () => {
    resetPaymentApiMocks({ payments: [] });
    renderAuthenticatedApp('/payments');
    expect(await screen.findByText(/no simulated payments/i)).toBeInTheDocument();
  });

  it('creates a payment and navigates to detail', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp('/payments/new');
    const referenceInput = await screen.findByLabelText(/merchant reference/i);

    await user.type(referenceInput, 'order_new');
    await user.type(screen.getByLabelText(/amount \(inr\)/i), '99.99');
    await user.click(screen.getByRole('button', { name: /create simulated payment/i }));

    await waitFor(() => {
      expect(paymentApiMocks.createPayment).toHaveBeenCalled();
    });
    const [, key] = paymentApiMocks.createPayment.mock.calls[0];
    expect(typeof key).toBe('string');
    expect(key.length).toBeGreaterThanOrEqual(8);
    expect(await screen.findByTestId('payment-detail')).toBeInTheDocument();
  });

  it('preserves idempotency key across uncertain create retry', async () => {
    const user = userEvent.setup();
    paymentApiMocks.createPayment
      .mockRejectedValueOnce(new ApiClientError(0, 'timeout', 'Request timed out'))
      .mockResolvedValueOnce({
        ...mockPayments[0],
        paymentId: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
        merchantReference: 'order_retry',
      });

    renderAuthenticatedApp('/payments/new');
    await user.type(await screen.findByLabelText(/merchant reference/i), 'order_retry');
    await user.type(screen.getByLabelText(/amount \(inr\)/i), '10.00');
    await user.click(screen.getByRole('button', { name: /create simulated payment/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/idempotency-key/i);
    await user.click(screen.getByRole('button', { name: /retry same request/i }));

    await waitFor(() => {
      expect(paymentApiMocks.createPayment).toHaveBeenCalledTimes(2);
    });
    expect(paymentApiMocks.createPayment.mock.calls[0][1]).toBe(
      paymentApiMocks.createPayment.mock.calls[1][1],
    );
    expect(paymentApiMocks.createPayment.mock.calls[0][0]).toEqual(
      paymentApiMocks.createPayment.mock.calls[1][0],
    );
  });

  it('authorizes a CREATED payment', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp(`/payments/${mockPayments[0].paymentId}`);
    await screen.findByTestId('payment-detail');
    await user.click(screen.getByRole('button', { name: /authorize \(simulated\)/i }));
    await waitFor(() => {
      expect(paymentApiMocks.authorizePayment).toHaveBeenCalledWith(mockPayments[0].paymentId);
    });
    await waitFor(() => {
      expect(
        within(screen.getByTestId('payment-detail')).getAllByText('AUTHORIZED').length,
      ).toBeGreaterThan(0);
    });
  });

  it('captures with confirmation and handles invalid transition refresh', async () => {
    const user = userEvent.setup();
    const authorized = {
      ...mockPayments[0],
      status: 'AUTHORIZED' as const,
      authorizedAt: '2026-10-17T12:01:00Z',
    };
    resetPaymentApiMocks({ payments: [authorized, mockPayments[1]] });
    paymentApiMocks.getPayment.mockResolvedValue(authorized);
    paymentApiMocks.capturePayment.mockRejectedValueOnce(
      new ApiClientError(409, 'invalid_state_transition', 'Payment status CAPTURED cannot be captured'),
    );

    renderAuthenticatedApp(`/payments/${authorized.paymentId}`);
    await screen.findByTestId('payment-detail');
    await user.click(screen.getByRole('button', { name: /capture \(simulated\)/i }));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^capture$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/refreshing payment state/i);
    expect(paymentApiMocks.getPayment).toHaveBeenCalled();
  });

  it('cancels a CREATED payment after confirmation', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp(`/payments/${mockPayments[0].paymentId}`);
    await screen.findByTestId('payment-detail');
    await user.click(screen.getByRole('button', { name: /cancel payment/i }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: /cancel payment/i }));
    await waitFor(() => {
      expect(paymentApiMocks.cancelPayment).toHaveBeenCalledWith(mockPayments[0].paymentId);
    });
  });

  it('shows refund history and creates a partial refund', async () => {
    const user = userEvent.setup();
    renderAuthenticatedApp(`/payments/${mockPayments[1].paymentId}`);
    expect(await screen.findByTestId('payment-refunds-panel')).toBeInTheDocument();
    expect(await screen.findByText('refund_1')).toBeInTheDocument();

    await user.type(await screen.findByLabelText(/refund reference/i), 'refund_partial');
    await user.type(screen.getByLabelText(/amount \(inr\)/i), '50.00');
    await user.click(screen.getByRole('button', { name: /submit simulated refund/i }));

    await waitFor(() => {
      expect(refundApiMocks.createRefund).toHaveBeenCalledWith(
        mockPayments[1].paymentId,
        expect.objectContaining({
          merchantReference: 'refund_partial',
          amountMinor: 5000,
        }),
        expect.any(String),
      );
    });
  });

  it('preserves refund idempotency key on uncertain retry', async () => {
    const user = userEvent.setup();
    refundApiMocks.createRefund
      .mockRejectedValueOnce(new ApiClientError(0, 'network_error', 'Network request failed'))
      .mockResolvedValueOnce({
        refundId: '12121212-1212-1212-1212-121212121212',
        paymentId: mockPayments[1].paymentId,
        merchantId: mockMerchants[0].id,
        merchantReference: 'refund_retry',
        amountMinor: 1000,
        currency: 'INR',
        status: 'SUCCEEDED',
        failureCode: null,
        completedAt: '2026-10-17T14:00:00Z',
        createdAt: '2026-10-17T14:00:00Z',
        updatedAt: '2026-10-17T14:00:00Z',
      });

    renderAuthenticatedApp(`/payments/${mockPayments[1].paymentId}`);
    await screen.findByTestId('payment-refunds-panel');
    await user.type(screen.getByLabelText(/refund reference/i), 'refund_retry');
    await user.type(screen.getByLabelText(/amount \(inr\)/i), '10.00');
    await user.click(screen.getByRole('button', { name: /submit simulated refund/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/idempotency-key/i);
    await user.click(screen.getByRole('button', { name: /retry same refund/i }));

    await waitFor(() => {
      expect(refundApiMocks.createRefund).toHaveBeenCalledTimes(2);
    });
    expect(refundApiMocks.createRefund.mock.calls[0][2]).toBe(
      refundApiMocks.createRefund.mock.calls[1][2],
    );
  });

  it('isolates payment list caches when switching merchants', async () => {
    const user = userEvent.setup();
    const queryClient = createTestQueryClient();
    renderAuthenticatedApp('/payments', { queryClient });
    await screen.findByTestId('payments-table');

    const probe = [...paymentKeys.lists(mockMerchants[0].id), 'probe'] as const;
    queryClient.setQueryData(probe, { leak: true });

    await user.click(screen.getByRole('button', { name: /merchant selector/i }));
    await user.click(screen.getByRole('option', { name: /beta labs/i }));

    await waitFor(() => {
      expect(queryClient.getQueryData(probe)).toBeUndefined();
    });
  });

  it('lists captured payments on refunds workflow page', async () => {
    renderAuthenticatedApp('/refunds');
    expect(await screen.findByTestId('refunds-scope-note')).toBeInTheDocument();
    expect(await screen.findByTestId('refundable-payments-table')).toBeInTheDocument();
    expect(screen.getByText('order_1002')).toBeInTheDocument();
    expect(screen.queryByText('order_1001')).not.toBeInTheDocument();
  });
});
