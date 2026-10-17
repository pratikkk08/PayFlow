import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthContext';
import { LoadingState } from '@/components/LoadingState';

/**
 * Usability guard for dashboard routes. Backend JWT enforcement remains authoritative.
 */
export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'bootstrapping') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-pf-surface">
        <LoadingState label="Restoring session…" />
      </div>
    );
  }

  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
