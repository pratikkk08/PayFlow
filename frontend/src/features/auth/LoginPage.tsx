import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Shield } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthContext';
import { mapAuthError } from '@/features/auth/authErrors';
import { validateLogin } from '@/features/auth/validation';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/LoadingState';

type LoginLocationState = {
  from?: string;
  registered?: boolean;
  email?: string;
};

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = (location.state as LoginLocationState | null) ?? null;
  const from =
    locationState?.from && locationState.from.startsWith('/')
      ? locationState.from
      : '/dashboard';

  const [email, setEmail] = useState(locationState?.email ?? '');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<ReturnType<typeof validateLogin>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const registeredNotice = Boolean(locationState?.registered);

  if (status === 'bootstrapping') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-pf-surface">
        <LoadingState label="Checking session…" />
      </div>
    );
  }

  if (status === 'authenticated') {
    return <Navigate to={from.startsWith('/') ? from : '/dashboard'} replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const errors = validateLogin(email, password);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await login({ email: email.trim(), password });
      navigate(from.startsWith('/') ? from : '/dashboard', { replace: true });
    } catch (error) {
      setFormError(mapAuthError(error, 'Unable to sign in'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-8 px-6 py-16">
      <div className="space-y-3">
        <div className="flex items-center gap-3 text-pf-primary">
          <Shield className="h-7 w-7" aria-hidden />
          <p className="text-sm font-semibold tracking-wide uppercase">PayFlow</p>
        </div>
        <h1 className="text-3xl font-semibold tracking-tight text-pf-ink">Sign in</h1>
        <p className="text-sm text-pf-muted">
          Access the operator console with your sandbox account.
        </p>
      </div>

      {registeredNotice ? (
        <p
          className="rounded-pf border border-pf-success/30 bg-pf-success-soft px-3 py-2 text-sm text-pf-success"
          role="status"
        >
          Account created. Sign in to continue.
        </p>
      ) : null}

      <form className="space-y-4" onSubmit={onSubmit} noValidate>
        <Input
          label="Email"
          type="email"
          name="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          disabled={submitting}
          required
        />
        <Input
          label="Password"
          type="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          disabled={submitting}
          required
        />

        {formError ? (
          <p className="rounded-pf border border-pf-danger/30 bg-pf-danger-soft px-3 py-2 text-sm text-pf-danger" role="alert">
            {formError}
          </p>
        ) : null}

        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>

      <p className="text-sm text-pf-muted">
        No account yet?{' '}
        <Link to="/register" className="font-medium text-pf-primary underline-offset-4 hover:underline">
          Create one
        </Link>
      </p>
    </main>
  );
}
