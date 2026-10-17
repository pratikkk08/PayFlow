import type { AxiosInstance } from 'axios';
import { apiClient, ApiClientError } from '@/lib/apiClient';
import { csrfHeaders } from '@/lib/csrf';
import type {
  BrowserAccessTokenResponse,
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from '@/types/api';

let refreshInFlight: Promise<BrowserAccessTokenResponse> | null = null;

async function ensureCsrf(client: AxiosInstance = apiClient): Promise<void> {
  await client.get('/api/v1/auth/browser/csrf', {
    withCredentials: true,
    headers: { Accept: 'application/json' },
  });
}

export async function browserLogin(
  credentials: LoginRequest,
  client: AxiosInstance = apiClient,
): Promise<BrowserAccessTokenResponse> {
  await ensureCsrf(client);
  const { data } = await client.post<BrowserAccessTokenResponse>(
    '/api/v1/auth/browser/login',
    credentials,
    {
      withCredentials: true,
      headers: csrfHeaders(),
    },
  );
  return data;
}

/**
 * Rotate the HttpOnly refresh cookie and return a new access token.
 * Concurrent callers share one in-flight request (avoids refresh-reuse revocation
 * under React Strict Mode double-mount and parallel 401 retries).
 */
export async function browserRefresh(
  client: AxiosInstance = apiClient,
): Promise<BrowserAccessTokenResponse> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      await ensureCsrf(client);
      const { data } = await client.post<BrowserAccessTokenResponse>(
        '/api/v1/auth/browser/refresh',
        null,
        {
          withCredentials: true,
          headers: csrfHeaders(),
        },
      );
      return data;
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export async function browserLogout(client: AxiosInstance = apiClient): Promise<void> {
  try {
    await ensureCsrf(client);
    await client.post('/api/v1/auth/browser/logout', null, {
      withCredentials: true,
      headers: csrfHeaders(),
    });
  } catch (error) {
    // Logout should clear local session even if the server session is already gone.
    if (!(error instanceof ApiClientError && (error.status === 401 || error.status === 403))) {
      throw error;
    }
  }
}

export async function registerUser(
  payload: RegisterRequest,
  client: AxiosInstance = apiClient,
): Promise<UserResponse> {
  const { data } = await client.post<UserResponse>('/api/v1/auth/register', payload);
  return data;
}

export async function fetchCurrentUser(client: AxiosInstance = apiClient): Promise<UserResponse> {
  const { data } = await client.get<UserResponse>('/api/v1/users/me');
  return data;
}

/** Test helper */
export function __resetBrowserRefreshInFlightForTests(): void {
  refreshInFlight = null;
}
