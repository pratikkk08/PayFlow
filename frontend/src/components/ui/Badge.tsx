import type { ReactNode } from 'react';

type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

type BadgeProps = {
  children: ReactNode;
  tone?: BadgeTone;
};

const toneClasses: Record<BadgeTone, string> = {
  neutral: 'bg-slate-100 text-slate-700',
  info: 'bg-pf-primary-soft text-pf-primary',
  success: 'bg-pf-success-soft text-pf-success',
  warning: 'bg-pf-warning-soft text-pf-warning',
  danger: 'bg-pf-danger-soft text-pf-danger',
};

export function Badge({ children, tone = 'neutral' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${toneClasses[tone]}`}
    >
      {children}
    </span>
  );
}
