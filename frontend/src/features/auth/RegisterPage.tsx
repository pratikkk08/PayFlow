import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { Shield } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthContext';
import { mapAuthError } from '@/features/auth/authErrors';
import { validateRegistration } from '@/features/auth/validation';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/LoadingState';

export function RegisterPage() {
  const { status, register } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<ReturnType<typeof validateRegistration>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'bootstrapping') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-pf-surface">
        <LoadingState label="Checking session…" />
      </div>
    );
  }

  if (status === 'authenticated') {
    return <Navigate to="/dashboard" replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const errors = validateRegistration(fullName, email, password);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
      });
      navigate('/login', {
        replace: true,
        state: { registered: true, email: email.trim() },
      });
    } catch (error) {
      setFormError(mapAuthError(error, 'Unable to create account'));
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
        <h1 className="text-3xl font-semibold tracking-tight text-pf-ink">Create account</h1>
        <p className="text-sm text-pf-muted">
          Registers a USER sandbox account. ADMIN is never self-assigned.
        </p>
      </div>

      <form className="space-y-4" onSubmit={onSubmit} noValidate>
        <Input
          label="Full name"
          name="fullName"
          autoComplete="name"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          error={fieldErrors.fullName}
          disabled={submitting}
          required
        />
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
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          hint="At least 10 characters with upper, lower, and a digit"
          disabled={submitting}
          required
        />

        {formError ? (
          <p className="rounded-pf border border-pf-danger/30 bg-pf-danger-soft px-3 py-2 text-sm text-pf-danger" role="alert">
            {formError}
          </p>
        ) : null}

        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </Button>
      </form>

      <p className="text-sm text-pf-muted">
        Already registered?{' '}
        <Link to="/login" className="font-medium text-pf-primary underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </main>
  );
}
