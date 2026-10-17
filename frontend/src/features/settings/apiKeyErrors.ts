import { ApiClientError } from '@/lib/apiClient';

export function mapApiKeyError(error: unknown, fallback: string): string {
  if (error instanceof ApiClientError) {
    if (error.status === 400) {
      return error.message || 'Please check your input and try again.';
    }
    if (error.status === 401) {
      return 'Your session expired. Sign in again.';
    }
    if (error.status === 403) {
      return 'You do not have permission for this action.';
    }
    if (error.status === 404) {
      return error.message || 'API key or merchant not found.';
    }
    if (error.status === 409) {
      return error.message || 'Conflict with existing data.';
    }
    if (error.status >= 500) {
      return 'Server error. Please try again shortly.';
    }
    return error.message || fallback;
  }
  return fallback;
}
