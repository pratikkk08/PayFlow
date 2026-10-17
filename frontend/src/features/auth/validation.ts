/** Client-side checks aligned with backend RegisterRequest / LoginRequest constraints. */

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;

export type FieldErrors = Partial<Record<'fullName' | 'email' | 'password', string>>;

export function validateLogin(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!email.trim()) {
    errors.email = 'Email is required';
  } else if (!EMAIL_RE.test(email.trim()) || email.trim().length > 320) {
    errors.email = 'Enter a valid email address';
  }
  if (!password) {
    errors.password = 'Password is required';
  } else if (password.length > 128) {
    errors.password = 'Password is too long';
  }
  return errors;
}

export function validateRegistration(
  fullName: string,
  email: string,
  password: string,
): FieldErrors {
  const errors: FieldErrors = {};
  const name = fullName.trim();
  if (!name) {
    errors.fullName = 'Full name is required';
  } else if (name.length < 2 || name.length > 200) {
    errors.fullName = 'Full name must be between 2 and 200 characters';
  }

  if (!email.trim()) {
    errors.email = 'Email is required';
  } else if (!EMAIL_RE.test(email.trim()) || email.trim().length > 320) {
    errors.email = 'Enter a valid email address';
  }

  if (!password) {
    errors.password = 'Password is required';
  } else if (password.length < 10 || password.length > 128) {
    errors.password = 'Password must be 10–128 characters';
  } else if (!PASSWORD_RE.test(password)) {
    errors.password = 'Password must include upper, lower, and digit characters';
  }

  return errors;
}
