import { ApiClientError } from '@/lib/apiClient';

export function mapAuthError(error: unknown, fallback: string): string {
  if (error instanceof ApiClientError) {
    if (error.code === 'email_taken') {
      return 'An account with this email already exists';
    }
    if (error.code === 'invalid_credentials') {
      return 'Invalid email or password';
    }
    if (error.status === 400) {
      return error.message || 'Please check your input and try again';
    }
    if (error.status === 403) {
      return 'Request blocked. Refresh the page and try again.';
    }
    if (error.status === 409) {
      return error.message || 'Conflict with existing data';
    }
    if (error.status >= 500) {
      return 'Server error. Please try again shortly.';
    }
    return error.message || fallback;
  }
  return fallback;
}
