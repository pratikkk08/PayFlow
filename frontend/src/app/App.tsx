import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { AppErrorBoundary } from '@/components/AppErrorBoundary';
import { AppRouter } from '@/app/router';
import { AuthProvider } from '@/features/auth/AuthContext';
import { MerchantProvider } from '@/features/merchants/MerchantContext';
import { queryClient } from '@/lib/queryClient';

export function App() {
  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <MerchantProvider>
              <AppRouter />
            </MerchantProvider>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </AppErrorBoundary>
  );
}
