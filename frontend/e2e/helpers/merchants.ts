import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

export async function createMerchant(
  page: Page,
  name: string,
  description?: string,
): Promise<void> {
  await page.goto('/merchants/new');
  await expect(page.getByRole('heading', { name: /create merchant/i })).toBeVisible();
  await page.getByLabel(/^merchant name$/i).fill(name);
  if (description) {
    await page.getByLabel(/^description$/i).fill(description);
  }
  await page.getByRole('button', { name: /^create merchant$/i }).click();
  await expect(page).toHaveURL(/\/merchants$/);
  await expect(page.getByTestId('merchant-list')).toContainText(name);
}

export async function selectMerchant(page: Page, name: string): Promise<void> {
  await page.getByRole('button', { name: /merchant selector/i }).click();
  const listbox = page.getByRole('listbox', { name: /owned merchants/i });
  await expect(listbox).toBeVisible();
  await listbox.getByRole('option', { name: new RegExp(escapeRegExp(name)) }).click();
  await expect(page.getByRole('button', { name: /merchant selector/i })).toContainText(name);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
