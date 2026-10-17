/** Client checks aligned with CreateMerchantRequest / UpdateMerchantRequest. */

export type MerchantFieldErrors = Partial<Record<'name' | 'description', string>>;

export function validateMerchantForm(name: string, description: string): MerchantFieldErrors {
  const errors: MerchantFieldErrors = {};
  const trimmedName = name.trim();
  if (!trimmedName) {
    errors.name = 'Merchant name is required';
  } else if (trimmedName.length < 2 || trimmedName.length > 200) {
    errors.name = 'Name must be between 2 and 200 characters';
  }

  if (description.length > 2000) {
    errors.description = 'Description must be at most 2000 characters';
  }

  return errors;
}
