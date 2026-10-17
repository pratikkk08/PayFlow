import { test, expect } from '@playwright/test';
import { loginUser, logout, registerUser } from './helpers/auth';
import { uniqueTestUser } from './helpers/credentials';

test.describe('Authentication', () => {
  test('register, login, session restore, and logout', async ({ page }) => {
    const user = uniqueTestUser('auth');

    await registerUser(page, user);
    await loginUser(page, user);

    await expect(page.getByRole('heading', { name: /^overview$/i })).toBeVisible();
    await expect(
      page.getByText(/create your first merchant|select a merchant|simulated payment activity/i),
    ).toBeVisible();

    await page.reload();
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByRole('heading', { name: /^overview$/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /account menu/i })).toBeVisible();

    await logout(page);

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /^sign in$/i })).toBeVisible();
  });
});
