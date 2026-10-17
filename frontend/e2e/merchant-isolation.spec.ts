import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';
import { uniqueMerchantName, uniqueTestUser } from './helpers/credentials';
import { createMerchant, selectMerchant } from './helpers/merchants';

test.describe('Merchant isolation', () => {
  test('switching merchants updates selection and clears prior analytics context', async ({
    page,
  }) => {
    const user = uniqueTestUser('merchant');
    const merchantA = uniqueMerchantName('Alpha');
    const merchantB = uniqueMerchantName('Beta');

    await registerAndLogin(page, user);
    await createMerchant(page, merchantA, 'Isolation merchant A');
    await createMerchant(page, merchantB, 'Isolation merchant B');

    await page.goto('/dashboard');
    await selectMerchant(page, merchantA);
    await expect(page.getByTestId('selected-merchant-summary')).toContainText(merchantA);
    await expect(page.getByTestId('selected-merchant-summary')).not.toContainText(merchantB);
    await expect(page.getByText(/no simulated activity yet/i)).toBeVisible();

    await selectMerchant(page, merchantB);
    await expect(page.getByTestId('selected-merchant-summary')).toContainText(merchantB);
    await expect(page.getByTestId('selected-merchant-summary')).not.toContainText(merchantA);
    await expect(page.getByText(/no simulated activity yet/i)).toBeVisible();
    await expect(page.getByTestId('analytics-metric-cards')).toBeVisible();
    await expect(page.getByTestId('metric-total-payments')).toContainText('0');
    await expect(page.getByTestId('metric-captured-amount')).toContainText('₹0.00');
  });
});
