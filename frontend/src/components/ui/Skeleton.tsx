type SkeletonProps = {
  className?: string;
};

export function Skeleton({ className = '' }: SkeletonProps) {
  return (
    <div
      className={`animate-pulse rounded-pf bg-slate-200/80 ${className}`}
      aria-hidden
      data-testid="skeleton"
    />
  );
}
