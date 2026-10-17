import { useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getMerchant } from '@/features/merchants/merchantApi';
import { merchantKeys } from '@/features/merchants/merchantKeys';
import { MerchantForm } from '@/features/merchants/MerchantForm';
import { MerchantStatusBadge } from '@/features/merchants/MerchantStatusBadge';
import { useUpdateMerchantMutation } from '@/features/merchants/useMerchantMutations';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { ApiClientError } from '@/lib/apiClient';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { PageHeader } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeleton';

export function EditMerchantPage() {
  const { merchantId = '' } = useParams();
  const navigate = useNavigate();
  const { merchants } = useMerchantSelection();
  const fromList = useMemo(
    () => merchants.find((m) => m.id === merchantId),
    [merchants, merchantId],
  );

  const detailQuery = useQuery({
    queryKey: merchantKeys.detail(merchantId),
    queryFn: () => getMerchant(merchantId),
    enabled: Boolean(merchantId),
    initialData: fromList,
  });

  const updateMutation = useUpdateMerchantMutation(merchantId);
  const merchant = detailQuery.data;

  if (detailQuery.isLoading && !merchant) {
    return (
      <div className="mx-auto max-w-xl space-y-4" aria-busy="true">
        <Skeleton className="h-8 w-48" />
        <Card className="space-y-3">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-24 w-full" />
        </Card>
      </div>
    );
  }

  if (detailQuery.isError || !merchant) {
    const message =
      detailQuery.error instanceof ApiClientError
        ? detailQuery.error.message
        : 'Merchant was not found or is no longer accessible.';
    return (
      <ErrorState
        title="Unable to edit merchant"
        description={message}
        action={
          <Link to="/merchants">
            <Button variant="secondary">Back to merchants</Button>
          </Link>
        }
      />
    );
  }

  return (
    <div className="mx-auto max-w-xl">
      <PageHeader
        title="Edit merchant"
        description="Update name and description. Ownership and status are managed by the platform."
        actions={<MerchantStatusBadge status={merchant.status} />}
      />
      <Card>
        <MerchantForm
          key={merchant.id + merchant.updatedAt}
          mode="edit"
          initialName={merchant.name}
          initialDescription={merchant.description}
          submitLabel="Save changes"
          onCancel={() => navigate('/merchants')}
          onSubmit={async (values) => {
            await updateMutation.mutateAsync({
              name: values.name,
              description: values.description,
            });
            navigate('/merchants', { replace: true });
          }}
        />
      </Card>
    </div>
  );
}
