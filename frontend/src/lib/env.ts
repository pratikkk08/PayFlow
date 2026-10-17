/**
 * Vite public config only. Never read secrets from import.meta.env.
 * Local development should leave VITE_API_BASE_URL empty and use the `/api` proxy.
 */
export function getApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL?.trim();
  if (configured) {
    return configured.replace(/\/$/, '');
  }
  return '';
}
