import { useNavigate } from 'react-router-dom';
import { MerchantForm } from '@/features/merchants/MerchantForm';
import { useCreateMerchantMutation } from '@/features/merchants/useMerchantMutations';
import { Card } from '@/components/ui/Card';
import { PageHeader } from '@/components/ui/PageHeader';

export function CreateMerchantPage() {
  const navigate = useNavigate();
  const createMutation = useCreateMerchantMutation();

  return (
    <div className="mx-auto max-w-xl">
      <PageHeader
        title="Create merchant"
        description="Registers a new ACTIVE sandbox merchant owned by your account."
      />
      <Card>
        <MerchantForm
          mode="create"
          submitLabel="Create merchant"
          onCancel={() => navigate('/merchants')}
          onSubmit={async (values) => {
            await createMutation.mutateAsync({
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
