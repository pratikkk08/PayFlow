import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  browserLogin,
  browserLogout,
  browserRefresh,
  fetchCurrentUser,
  registerUser,
} from '@/features/auth/authApi';
import {
  clearAccessToken,
  setAccessToken,
  setSessionClearedListener,
} from '@/lib/authTokenStore';
import type { LoginRequest, RegisterRequest, UserResponse } from '@/types/api';

export type AuthStatus = 'bootstrapping' | 'authenticated' | 'anonymous';

type AuthContextValue = {
  status: AuthStatus;
  user: UserResponse | null;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<UserResponse>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('bootstrapping');
  const [user, setUser] = useState<UserResponse | null>(null);

  const clearSession = useCallback(() => {
    clearAccessToken();
    setUser(null);
    setStatus('anonymous');
  }, []);

  useEffect(() => {
    setSessionClearedListener(() => {
      setUser(null);
      setStatus('anonymous');
    });
    return () => setSessionClearedListener(null);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function restore() {
      try {
        const tokens = await browserRefresh();
        // Persist access token even if this effect instance is cancelled (Strict Mode),
        // so a remount can reuse memory state without forcing another rotation race.
        setAccessToken(tokens.accessToken);
        const me = await fetchCurrentUser();
        if (cancelled) {
          return;
        }
        setUser(me);
        setStatus('authenticated');
      } catch {
        if (!cancelled) {
          clearAccessToken();
          setUser(null);
          setStatus('anonymous');
        }
      }
    }

    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (credentials: LoginRequest) => {
    try {
      const tokens = await browserLogin(credentials);
      setAccessToken(tokens.accessToken);
      const me = await fetchCurrentUser();
      setUser(me);
      setStatus('authenticated');
    } catch (error) {
      clearAccessToken();
      setUser(null);
      setStatus('anonymous');
      throw error;
    }
  }, []);

  const register = useCallback(async (payload: RegisterRequest) => {
    return registerUser(payload);
  }, []);

  const logout = useCallback(async () => {
    try {
      await browserLogout();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const refreshUser = useCallback(async () => {
    const me = await fetchCurrentUser();
    setUser(me);
    setStatus('authenticated');
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout, refreshUser }),
    [status, user, login, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// Context + hook co-located by design (standard React pattern).
// eslint-disable-next-line react-refresh/only-export-components -- useAuth pairs with AuthProvider
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
