import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRouter } from '@/app/router';
import { AuthProvider } from '@/features/auth/AuthContext';
import { MerchantProvider } from '@/features/merchants/MerchantContext';

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

export function renderAuthenticatedApp(path: string, options?: { queryClient?: QueryClient }) {
  const queryClient = options?.queryClient ?? createTestQueryClient();
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[path]}>
          <AuthProvider>
            <MerchantProvider>
              <AppRouter />
            </MerchantProvider>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}
