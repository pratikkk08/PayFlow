import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';
import {
  clearAccessToken,
  getAccessToken,
  notifySessionCleared,
  setAccessToken,
} from '@/lib/authTokenStore';
import { getApiBaseUrl } from '@/lib/env';

export type ApiErrorBody = {
  status?: number;
  error?: string;
  message?: string;
  path?: string;
};

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly path?: string;

  constructor(status: number, code: string, message: string, path?: string) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.code = code;
    this.path = path;
  }
}

type RetriableConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
  _skipAuthRefresh?: boolean;
};

function isBrowserAuthPath(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  return (
    url.includes('/api/v1/auth/browser/login') ||
    url.includes('/api/v1/auth/browser/refresh') ||
    url.includes('/api/v1/auth/browser/logout') ||
    url.includes('/api/v1/auth/browser/csrf') ||
    url.includes('/api/v1/auth/register') ||
    url.includes('/api/v1/auth/login')
  );
}

function toApiClientError(error: AxiosError<ApiErrorBody>): ApiClientError {
  if (error.response) {
    const body = error.response.data;
    return new ApiClientError(
      error.response.status,
      body?.error ?? 'http_error',
      body?.message ?? error.message,
      body?.path,
    );
  }
  if (error.code === 'ECONNABORTED') {
    return new ApiClientError(0, 'timeout', 'Request timed out');
  }
  return new ApiClientError(0, 'network_error', error.message || 'Network request failed');
}

let refreshInFlight: Promise<string> | null = null;

async function rotateAccessToken(client: AxiosInstance): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const { browserRefresh } = await import('@/features/auth/authApi');
      const tokens = await browserRefresh(client);
      setAccessToken(tokens.accessToken);
      return tokens.accessToken;
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/**
 * Shared Axios client for browser → backend calls.
 *
 * Access JWT lives in memory only. Refresh uses HttpOnly cookie via /api/v1/auth/browser/*.
 * Never log tokens.
 */
export function createApiClient(baseURL: string = getApiBaseUrl()): AxiosInstance {
  const client = axios.create({
    baseURL,
    timeout: 15_000,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    withCredentials: false,
  });

  client.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ApiErrorBody>) => {
      const config = error.config as RetriableConfig | undefined;
      const status = error.response?.status;

      if (
        status === 401 &&
        config &&
        !config._retry &&
        !config._skipAuthRefresh &&
        !isBrowserAuthPath(config.url)
      ) {
        config._retry = true;
        try {
          const newToken = await rotateAccessToken(client);
          config.headers.Authorization = `Bearer ${newToken}`;
          return client.request(config);
        } catch {
          clearAccessToken();
          notifySessionCleared();
          throw toApiClientError(error);
        }
      }

      throw toApiClientError(error);
    },
  );

  return client;
}

export const apiClient = createApiClient();

/** Test helper — reset client-level refresh single-flight. */
export function __resetRefreshInFlightForTests(): void {
  refreshInFlight = null;
}
