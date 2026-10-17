import { Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { DashboardOverviewPage } from '@/features/dashboard/DashboardOverviewPage';
import { HomePage } from '@/features/home/HomePage';
import { NotFoundPage } from '@/features/home/NotFoundPage';
import { CreateMerchantPage } from '@/features/merchants/CreateMerchantPage';
import { EditMerchantPage } from '@/features/merchants/EditMerchantPage';
import { MerchantsPage } from '@/features/merchants/MerchantsPage';
import { CreatePaymentPage } from '@/features/payments/CreatePaymentPage';
import { PaymentDetailPage } from '@/features/payments/PaymentDetailPage';
import { PaymentsPage } from '@/features/payments/PaymentsPage';
import { RefundsPage } from '@/features/refunds/RefundsPage';
import { ApiKeysPage } from '@/features/settings/ApiKeysPage';
import { SettingsPage } from '@/features/settings/SettingsPage';
import { WebhooksPage } from '@/features/webhooks/WebhooksPage';

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<DashboardLayout />}>
          <Route path="/dashboard" element={<DashboardOverviewPage />} />
          <Route path="/payments" element={<PaymentsPage />} />
          <Route path="/payments/new" element={<CreatePaymentPage />} />
          <Route path="/payments/:paymentId" element={<PaymentDetailPage />} />
          <Route path="/refunds" element={<RefundsPage />} />
          <Route path="/merchants" element={<MerchantsPage />} />
          <Route path="/merchants/new" element={<CreateMerchantPage />} />
          <Route path="/merchants/:merchantId/edit" element={<EditMerchantPage />} />
          <Route path="/api-keys" element={<ApiKeysPage />} />
          <Route path="/webhooks" element={<WebhooksPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
