/** TanStack Query keys — include merchantId for future merchant-scoped caches. */
export const merchantKeys = {
  all: ['merchants'] as const,
  lists: () => [...merchantKeys.all, 'list'] as const,
  list: (userId: string) => [...merchantKeys.lists(), userId] as const,
  details: () => [...merchantKeys.all, 'detail'] as const,
  detail: (merchantId: string) => [...merchantKeys.details(), merchantId] as const,
  scoped: (merchantId: string) => [...merchantKeys.all, 'scoped', merchantId] as const,
};
