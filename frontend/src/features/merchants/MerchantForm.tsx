import { useState, type FormEvent } from 'react';
import { ApiClientError } from '@/lib/apiClient';
import { validateMerchantForm } from '@/features/merchants/validation';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

type MerchantFormProps = {
  mode: 'create' | 'edit';
  initialName?: string;
  initialDescription?: string | null;
  submitLabel: string;
  onSubmit: (values: { name: string; description: string | null }) => Promise<void>;
  onCancel?: () => void;
};

export function MerchantForm({
  mode,
  initialName = '',
  initialDescription = '',
  submitLabel,
  onSubmit,
  onCancel,
}: MerchantFormProps) {
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription ?? '');
  const [fieldErrors, setFieldErrors] = useState<ReturnType<typeof validateMerchantForm>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const errors = validateMerchantForm(name, description);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      const trimmedDescription = description.trim();
      await onSubmit({
        name: name.trim(),
        description: trimmedDescription.length > 0 ? trimmedDescription : null,
      });
    } catch (error) {
      setFormError(mapMerchantError(error, mode));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="space-y-4" onSubmit={(e) => void handleSubmit(e)} noValidate>
      <Input
        label="Merchant name"
        name="name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        error={fieldErrors.name}
        disabled={submitting}
        required
        autoComplete="organization"
      />
      <div className="flex w-full flex-col gap-1.5">
        <label htmlFor="merchant-description" className="text-sm font-medium text-pf-ink-soft">
          Description
        </label>
        <textarea
          id="merchant-description"
          name="description"
          rows={4}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          disabled={submitting}
          aria-invalid={fieldErrors.description ? true : undefined}
          className={`w-full rounded-pf border bg-pf-panel px-3 py-2 text-sm text-pf-ink shadow-pf-sm placeholder:text-slate-400 focus-visible:border-pf-primary ${
            fieldErrors.description ? 'border-pf-danger' : 'border-pf-border'
          }`}
          placeholder="Optional notes about this sandbox merchant"
        />
        {fieldErrors.description ? (
          <p className="text-xs text-pf-danger" role="alert">
            {fieldErrors.description}
          </p>
        ) : (
          <p className="text-xs text-pf-muted">Optional. Max 2000 characters.</p>
        )}
      </div>

      {formError ? (
        <p
          className="rounded-pf border border-pf-danger/30 bg-pf-danger-soft px-3 py-2 text-sm text-pf-danger"
          role="alert"
        >
          {formError}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={submitting}>
          {submitting ? (mode === 'create' ? 'Creating…' : 'Saving…') : submitLabel}
        </Button>
        {onCancel ? (
          <Button type="button" variant="secondary" disabled={submitting} onClick={onCancel}>
            Cancel
          </Button>
        ) : null}
      </div>
    </form>
  );
}

function mapMerchantError(error: unknown, mode: 'create' | 'edit'): string {
  if (error instanceof ApiClientError) {
    if (error.status === 400) {
      return error.message || 'Please check your input and try again';
    }
    if (error.status === 401) {
      return 'Your session expired. Sign in again.';
    }
    if (error.status === 403) {
      return 'You do not have permission to perform this action';
    }
    if (error.status === 404) {
      return 'Merchant was not found or is no longer accessible';
    }
    if (error.status >= 500) {
      return 'Server error. Please try again shortly.';
    }
    return error.message || (mode === 'create' ? 'Unable to create merchant' : 'Unable to update merchant');
  }
  return mode === 'create' ? 'Unable to create merchant' : 'Unable to update merchant';
}
