import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';
import {
  uniqueMerchantName,
  uniqueReference,
  uniqueTestUser,
} from './helpers/credentials';
import { createMerchant } from './helpers/merchants';

/**
 * Uses DeterministicSimulatedPaymentProcessor / RefundProcessor success paths:
 * - merchantReference must NOT start with sim_decline_ / sim_insufficient_ / sim_refund_decline_
 * - amountMinor must NOT be 1301 (insufficient_funds fixture)
 * New merchants have no risk rules, so authorize should reach AUTHORIZED (not REVIEW hold).
 */
test.describe('Simulated payment lifecycle', () => {
  test('create, authorize, capture, and partial refund', async ({ page }) => {
    const user = uniqueTestUser('pay');
    const merchantName = uniqueMerchantName('Pay');
    const paymentRef = uniqueReference('e2e_pay');
    const refundRef = uniqueReference('e2e_rf');

    await registerAndLogin(page, user);
    await createMerchant(page, merchantName);

    await page.goto('/payments/new');
    await expect(page.getByRole('heading', { name: /create payment/i })).toBeVisible();
    await page.getByLabel(/^merchant reference$/i).fill(paymentRef);
    // ₹100.00 → 10000 paise (not the 1301 decline fixture)
    await page.getByLabel(/^amount \(inr\)$/i).fill('100.00');
    await page.getByRole('button', { name: /create simulated payment/i }).click();

    await expect(page.getByTestId('payment-detail')).toBeVisible();
    await expect(page.getByText(paymentRef).first()).toBeVisible();
    await expect(page.getByText('CREATED').first()).toBeVisible();

    await page.getByRole('button', { name: /authorize \(simulated\)/i }).click();
    await expect(page.getByText('AUTHORIZED').first()).toBeVisible();
    await expect(page.getByText(/held for risk review/i)).toHaveCount(0);

    await page.getByRole('button', { name: /capture \(simulated\)/i }).click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await page.getByRole('button', { name: /^capture$/i }).click();
    await expect(page.getByText('CAPTURED').first()).toBeVisible();

    await expect(page.getByTestId('payment-refunds-panel')).toBeVisible();
    await page.getByLabel(/^refund reference$/i).fill(refundRef);
    await page.getByLabel(/^amount \(inr\)$/i).fill('25.00');
    await page.getByRole('button', { name: /submit simulated refund/i }).click();

    await expect(page.getByTestId('payment-refunds-panel')).toContainText(refundRef);
    await expect(page.getByTestId('payment-refunds-panel')).toContainText('₹25.00');
    await expect(page.getByTestId('payment-refunds-panel')).toContainText(/succeeded/i);

    await page.goto('/dashboard');
    await expect(page.getByTestId('selected-merchant-summary')).toContainText(merchantName);
    await expect(page.getByTestId('metric-total-payments')).toContainText('1');
    await expect(page.getByTestId('metric-captured-amount')).toContainText('₹100.00');
    await expect(page.getByTestId('metric-refund-amount')).toContainText('₹25.00');
  });
});
