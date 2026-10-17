import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/features/auth/AuthContext';
import { createMerchant, updateMerchant } from '@/features/merchants/merchantApi';
import { merchantKeys } from '@/features/merchants/merchantKeys';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import type {
  CreateMerchantRequest,
  MerchantResponse,
  UpdateMerchantRequest,
} from '@/types/api';

export function useCreateMerchantMutation() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { selectMerchant } = useMerchantSelection();
  const userId = user?.id;

  return useMutation({
    mutationFn: (payload: CreateMerchantRequest) => createMerchant(payload),
    onSuccess: async (created) => {
      if (userId) {
        queryClient.setQueryData<MerchantResponse[]>(merchantKeys.list(userId), (previous) =>
          previous ? [created, ...previous.filter((m) => m.id !== created.id)] : [created],
        );
        await queryClient.invalidateQueries({ queryKey: merchantKeys.list(userId) });
      }
      selectMerchant(created.id, { allowPending: true });
    },
  });
}

export function useUpdateMerchantMutation(merchantId: string) {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id;

  return useMutation({
    mutationFn: (payload: UpdateMerchantRequest) => updateMerchant(merchantId, payload),
    onSuccess: async (updated) => {
      if (userId) {
        queryClient.setQueryData<MerchantResponse[]>(merchantKeys.list(userId), (previous) =>
          previous
            ? previous.map((m) => (m.id === updated.id ? updated : m))
            : [updated],
        );
        await queryClient.invalidateQueries({ queryKey: merchantKeys.list(userId) });
      }
      queryClient.setQueryData(merchantKeys.detail(merchantId), updated);
    },
  });
}
