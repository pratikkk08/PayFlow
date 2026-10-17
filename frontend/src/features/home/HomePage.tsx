import { Link } from 'react-router-dom';
import { ArrowRight, Shield } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthContext';
import { Button } from '@/components/ui/Button';

/**
 * Public landing surface.
 * No fake metrics or simulated revenue — sandbox product identity only.
 */
export function HomePage() {
  const { status } = useAuth();
  const signedIn = status === 'authenticated';

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col justify-center gap-8 px-6 py-16">
      <div className="flex items-center gap-3 text-pf-primary">
        <Shield className="h-8 w-8" aria-hidden />
        <p className="text-sm font-semibold tracking-wide uppercase">PayFlow</p>
      </div>

      <div className="space-y-4">
        <h1 className="text-4xl font-semibold tracking-tight text-pf-ink sm:text-5xl">
          Simulated merchant payments
        </h1>
        <p className="max-w-xl text-lg text-pf-muted">
          Portfolio sandbox for merchants, API keys, payments, refunds, webhooks, risk, and
          analytics. Never processes real cards or live funds.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        {signedIn ? (
          <Link to="/dashboard">
            <Button>
              Open dashboard
              <ArrowRight className="h-4 w-4" aria-hidden />
            </Button>
          </Link>
        ) : (
          <>
            <Link to="/login">
              <Button>
                Sign in
                <ArrowRight className="h-4 w-4" aria-hidden />
              </Button>
            </Link>
            <Link to="/register">
              <Button variant="secondary">Create account</Button>
            </Link>
          </>
        )}
        <Link
          to="/does-not-exist"
          className="text-sm font-medium text-pf-primary underline-offset-4 hover:underline"
        >
          Open a missing route
        </Link>
      </div>
    </main>
  );
}
