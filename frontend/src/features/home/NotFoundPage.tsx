import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';

export function NotFoundPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-4 px-6">
      <p className="text-sm font-semibold tracking-wide text-pf-primary uppercase">404</p>
      <h1 className="text-3xl font-semibold text-pf-ink">Page not found</h1>
      <p className="text-pf-muted">That route is not part of the PayFlow UI yet.</p>
      <Link to="/">
        <Button>Back to home</Button>
      </Link>
    </main>
  );
}
