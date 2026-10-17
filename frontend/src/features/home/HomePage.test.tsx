import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '@/features/auth/AuthContext';
import { HomePage } from '@/features/home/HomePage';
import { clearAccessToken } from '@/lib/authTokenStore';
import { authApiMocks, resetAuthApiMocks } from '@/test/authMocks';

vi.mock('@/features/auth/authApi', () => ({
  browserLogin: (...args: unknown[]) => authApiMocks.browserLogin(...args),
  browserRefresh: (...args: unknown[]) => authApiMocks.browserRefresh(...args),
  browserLogout: (...args: unknown[]) => authApiMocks.browserLogout(...args),
  registerUser: (...args: unknown[]) => authApiMocks.registerUser(...args),
  fetchCurrentUser: (...args: unknown[]) => authApiMocks.fetchCurrentUser(...args),
}));

describe('HomePage', () => {
  beforeEach(() => {
    clearAccessToken();
    resetAuthApiMocks();
  });

  it('shows product identity without fake revenue stats', async () => {
    render(
      <MemoryRouter>
        <AuthProvider>
          <HomePage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(screen.getByText('PayFlow')).toBeInTheDocument();
    expect(screen.getByText(/never processes real cards or live funds/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('link', { name: /sign in/i })).toHaveAttribute('href', '/login');
    });
    expect(screen.getByRole('link', { name: /create account/i })).toHaveAttribute(
      'href',
      '/register',
    );
    expect(screen.queryByText(/revenue/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
  });
});
