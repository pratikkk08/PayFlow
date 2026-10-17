type LoadingSpinnerProps = {
  label?: string;
  size?: 'sm' | 'md';
};

export function LoadingSpinner({ label = 'Loading…', size = 'md' }: LoadingSpinnerProps) {
  const sizeClass = size === 'sm' ? 'h-4 w-4 border-2' : 'h-5 w-5 border-2';
  return (
    <div
      className="flex items-center gap-3 text-pf-muted"
      role="status"
      aria-live="polite"
      data-testid="loading-spinner"
    >
      <span
        className={`inline-block animate-spin rounded-full border-slate-300 border-t-pf-primary ${sizeClass}`}
        aria-hidden
      />
      <span className="text-sm">{label}</span>
    </div>
  );
}
