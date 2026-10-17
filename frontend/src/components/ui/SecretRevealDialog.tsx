import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Button } from '@/components/ui/Button';
import { copyTextToClipboard } from '@/lib/clipboard';

type SecretRevealDialogProps = {
  open: boolean;
  title: string;
  description: string;
  secret: string;
  secretLabel?: string;
  warning: string;
  onClose: () => void;
  footerNote?: ReactNode;
};

/**
 * One-time secret display. Caller owns clearing `secret` from React state on close.
 * Never writes the secret to storage or query caches.
 */
export function SecretRevealDialog({
  open,
  title,
  description,
  secret,
  secretLabel = 'Secret',
  warning,
  onClose,
  footerNote,
}: SecretRevealDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const secretId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setCopied(false);
      setCopyError(null);
      return;
    }
    closeRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  async function copySecret() {
    setCopyError(null);
    try {
      await copyTextToClipboard(secret);
      setCopied(true);
    } catch {
      setCopyError('Unable to copy. Select the secret and copy manually.');
      setCopied(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="presentation">
      <button
        type="button"
        className="absolute inset-0 bg-slate-900/40"
        aria-label="Dismiss secret dialog"
        onClick={onClose}
      />
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        data-testid="secret-reveal-dialog"
        className="relative z-10 w-full max-w-lg rounded-pf border border-pf-border bg-pf-panel p-5 shadow-pf-md"
      >
        <h2 id={titleId} className="text-lg font-semibold text-pf-ink">
          {title}
        </h2>
        <p id={descriptionId} className="mt-2 text-sm text-pf-muted">
          {description}
        </p>
        <p className="mt-3 rounded-pf border border-pf-warning/40 bg-pf-warning-soft px-3 py-2 text-sm text-pf-warning">
          {warning}
        </p>
        <div className="mt-4 space-y-1.5">
          <label htmlFor={secretId} className="text-sm font-medium text-pf-ink-soft">
            {secretLabel}
          </label>
          <input
            id={secretId}
            type="text"
            readOnly
            value={secret}
            autoComplete="off"
            spellCheck={false}
            data-testid="revealed-secret"
            className="w-full rounded-pf border border-pf-border bg-slate-50 px-3 py-2 font-mono text-sm text-pf-ink"
          />
        </div>
        {copyError ? (
          <p className="mt-2 text-xs text-pf-danger" role="alert">
            {copyError}
          </p>
        ) : null}
        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button
            variant="secondary"
            onClick={() => {
              void copySecret();
            }}
            data-testid="copy-secret"
          >
            {copied ? 'Copied' : 'Copy Secret'}
          </Button>
          <Button ref={closeRef} variant="primary" onClick={onClose} data-testid="close-secret-dialog">
            I have saved it
          </Button>
        </div>
        <p className="mt-3 text-xs text-pf-muted">
          Copied secrets may remain in the operating system clipboard until overwritten.
          {footerNote ? <> {footerNote}</> : null}
        </p>
      </div>
    </div>
  );
}
