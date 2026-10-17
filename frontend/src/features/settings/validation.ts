export type CreateApiKeyFormValues = {
  name: string;
  type: 'SECRET' | 'PUBLISHABLE';
};

export type CreateApiKeyFormResult =
  | { ok: true; name: string; type: 'SECRET' | 'PUBLISHABLE' }
  | { ok: false; errors: Partial<Record<'name', string>> };

export function validateCreateApiKeyForm(values: CreateApiKeyFormValues): CreateApiKeyFormResult {
  const name = values.name.trim();
  const errors: Partial<Record<'name', string>> = {};
  if (name.length < 2) {
    errors.name = 'Name must be at least 2 characters';
  } else if (name.length > 120) {
    errors.name = 'Name must be at most 120 characters';
  }
  if (errors.name) {
    return { ok: false, errors };
  }
  return { ok: true, name, type: values.type };
}
