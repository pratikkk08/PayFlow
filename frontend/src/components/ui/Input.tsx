import { useId, type InputHTMLAttributes } from 'react';

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
  error?: string;
};

export function Input({ label, hint, error, id, className = '', ...props }: InputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;

  return (
    <div className="flex w-full flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-medium text-pf-ink-soft">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hintId, errorId].filter(Boolean).join(' ') || undefined}
        className={`w-full rounded-pf border bg-pf-panel px-3 py-2 text-sm text-pf-ink shadow-pf-sm placeholder:text-slate-400 focus-visible:border-pf-primary ${
          error ? 'border-pf-danger' : 'border-pf-border'
        } ${className}`}
        {...props}
      />
      {hint && !error ? (
        <p id={hintId} className="text-xs text-pf-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-xs text-pf-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
