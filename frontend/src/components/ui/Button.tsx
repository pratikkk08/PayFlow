import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
type ButtonSize = 'sm' | 'md';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  children: ReactNode;
};

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-pf-primary text-white hover:bg-pf-primary-hover shadow-pf-sm disabled:bg-slate-300',
  secondary:
    'bg-pf-panel text-pf-ink border border-pf-border hover:bg-slate-50 shadow-pf-sm disabled:text-slate-400',
  ghost: 'bg-transparent text-pf-ink-soft hover:bg-slate-100 disabled:text-slate-400',
  danger: 'bg-pf-danger text-white hover:bg-red-700 shadow-pf-sm disabled:bg-slate-300',
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', className = '', type = 'button', children, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={`inline-flex items-center justify-center gap-2 rounded-pf font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-pf-primary disabled:cursor-not-allowed ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
});
