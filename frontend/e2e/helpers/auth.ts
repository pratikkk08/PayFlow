import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';
import type { E2eUser } from './credentials';

export async function registerUser(page: Page, user: E2eUser): Promise<void> {
  await page.goto('/register');
  await expect(page.getByRole('heading', { name: /create account/i })).toBeVisible();
  await page.getByLabel(/^full name$/i).fill(user.fullName);
  await page.getByLabel(/^email$/i).fill(user.email);
  await page.getByLabel(/^password$/i).fill(user.password);
  await page.getByRole('button', { name: /^create account$/i }).click();
  await expect(page.getByRole('heading', { name: /^sign in$/i })).toBeVisible();
  await expect(page.getByRole('status')).toContainText(/account created/i);
}

export async function loginUser(page: Page, user: E2eUser): Promise<void> {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: /^sign in$/i })).toBeVisible();
  await page.getByLabel(/^email$/i).fill(user.email);
  await page.getByLabel(/^password$/i).fill(user.password);
  await page.getByRole('button', { name: /^sign in$/i }).click();
  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByRole('heading', { name: /^overview$/i })).toBeVisible();
}

export async function registerAndLogin(page: Page, user: E2eUser): Promise<void> {
  await registerUser(page, user);
  await loginUser(page, user);
}

export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: /account menu/i }).click();
  await page.getByRole('menuitem', { name: /sign out/i }).click();
  await expect(page.getByRole('heading', { name: /^sign in$/i })).toBeVisible();
}
