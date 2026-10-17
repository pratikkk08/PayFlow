import { describe, expect, it } from 'vitest';
import { createApiClient } from '@/lib/apiClient';
import { getApiBaseUrl } from '@/lib/env';

describe('apiClient configuration', () => {
  it('defaults to empty base URL for Vite /api proxy', () => {
    expect(getApiBaseUrl()).toBe('');
  });

  it('creates an axios instance with timeout and JSON headers', () => {
    const client = createApiClient('');
    expect(client.defaults.timeout).toBe(15_000);
    expect(client.defaults.headers.Accept).toBe('application/json');
    expect(client.defaults.headers['Content-Type']).toBe('application/json');
    expect(client.defaults.baseURL).toBe('');
    expect(client.defaults.withCredentials).toBe(false);
  });
});
