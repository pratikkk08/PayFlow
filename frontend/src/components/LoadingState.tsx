import { LoadingSpinner } from '@/components/ui/LoadingSpinner';

type LoadingStateProps = {
  label?: string;
};

/** @deprecated Prefer LoadingSpinner — kept for compatibility with 7.1 call sites. */
export function LoadingState({ label = 'Loading…' }: LoadingStateProps) {
  return <LoadingSpinner label={label} />;
}
