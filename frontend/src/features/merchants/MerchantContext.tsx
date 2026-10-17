import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/features/auth/AuthContext';
import { listMerchants } from '@/features/merchants/merchantApi';
import { merchantKeys } from '@/features/merchants/merchantKeys';
import {
  clearStoredMerchantId,
  readStoredMerchantId,
  resolveMerchantSelection,
  writeStoredMerchantId,
} from '@/features/merchants/merchantSelectionStorage';
import type { MerchantResponse } from '@/types/api';

type MerchantContextValue = {
  merchants: MerchantResponse[];
  selectedMerchant: MerchantResponse | null;
  selectedMerchantId: string | null;
  selectMerchant: (merchantId: string, options?: { allowPending?: boolean }) => void;
  isLoading: boolean;
  isError: boolean;
  errorMessage: string | null;
  refetch: () => void;
};

const MerchantContext = createContext<MerchantContextValue | null>(null);

export function MerchantProvider({ children }: { children: ReactNode }) {
  const { status, user } = useAuth();
  const queryClient = useQueryClient();
  const userId = user?.id ?? null;
  const authenticated = status === 'authenticated' && Boolean(userId);
  const previousUserIdRef = useRef<string | null>(null);

  const [selectedMerchantId, setSelectedMerchantId] = useState<string | null>(null);

  const merchantsQuery = useQuery({
    queryKey: merchantKeys.list(userId ?? 'anonymous'),
    queryFn: listMerchants,
    enabled: authenticated,
  });

  const merchants = useMemo(
    () => merchantsQuery.data ?? [],
    [merchantsQuery.data],
  );

  useEffect(() => {
    if (userId) {
      previousUserIdRef.current = userId;
    }
  }, [userId]);

  // Clear caches and persisted preference when the session ends.
  useEffect(() => {
    if (status !== 'anonymous') {
      return;
    }
    if (previousUserIdRef.current) {
      clearStoredMerchantId(previousUserIdRef.current);
      previousUserIdRef.current = null;
    }
    queryClient.clear();
    setSelectedMerchantId(null);
  }, [status, queryClient]);

  useEffect(() => {
    if (!userId || status !== 'authenticated') {
      return;
    }
    // Drop previous account's in-memory selection; load this user's preference.
    setSelectedMerchantId(readStoredMerchantId(userId));
  }, [userId, status]);

  useEffect(() => {
    if (!userId || !merchantsQuery.isSuccess) {
      return;
    }
    const next = resolveMerchantSelection(merchants, selectedMerchantId);
    if (next !== selectedMerchantId) {
      setSelectedMerchantId(next);
    }
    if (next) {
      writeStoredMerchantId(userId, next);
    } else {
      clearStoredMerchantId(userId);
    }
  }, [userId, merchants, merchantsQuery.isSuccess, selectedMerchantId]);

  const selectMerchant = useCallback(
    (merchantId: string, options?: { allowPending?: boolean }) => {
      if (!userId) {
        return;
      }
      const known = merchants.some((m) => m.id === merchantId);
      if (!known && !options?.allowPending) {
        return;
      }
      setSelectedMerchantId(merchantId);
      writeStoredMerchantId(userId, merchantId);
      // Drop all merchant-scoped caches so stale data cannot leak across selections.
      queryClient.removeQueries({ queryKey: [...merchantKeys.all, 'scoped'] });
    },
    [userId, merchants, queryClient],
  );

  const selectedMerchant =
    merchants.find((m) => m.id === selectedMerchantId) ?? null;

  const value = useMemo<MerchantContextValue>(
    () => ({
      merchants,
      selectedMerchant,
      selectedMerchantId: selectedMerchant?.id ?? null,
      selectMerchant,
      isLoading: authenticated && merchantsQuery.isLoading,
      isError: merchantsQuery.isError,
      errorMessage: merchantsQuery.error instanceof Error ? merchantsQuery.error.message : null,
      refetch: () => {
        void merchantsQuery.refetch();
      },
    }),
    [
      merchants,
      selectedMerchant,
      selectMerchant,
      authenticated,
      merchantsQuery,
    ],
  );

  return <MerchantContext.Provider value={value}>{children}</MerchantContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- hook pairs with provider
export function useMerchantSelection(): MerchantContextValue {
  const ctx = useContext(MerchantContext);
  if (!ctx) {
    throw new Error('useMerchantSelection must be used within MerchantProvider');
  }
  return ctx;
}
