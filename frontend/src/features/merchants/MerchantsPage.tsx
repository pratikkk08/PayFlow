import { Link, useNavigate } from 'react-router-dom';
import { Building2, Pencil, Plus } from 'lucide-react';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { MerchantStatusBadge } from '@/features/merchants/MerchantStatusBadge';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { PageHeader } from '@/components/ui/PageHeader';
import { Skeleton } from '@/components/ui/Skeleton';

export function MerchantsPage() {
  const navigate = useNavigate();
  const {
    merchants,
    selectedMerchantId,
    selectMerchant,
    isLoading,
    isError,
    errorMessage,
    refetch,
  } = useMerchantSelection();

  return (
    <div>
      <PageHeader
        title="Merchants"
        description="Manage sandbox merchant accounts owned by your user. No live funds or card data."
        actions={
          <Button onClick={() => navigate('/merchants/new')}>
            <Plus className="h-4 w-4" aria-hidden />
            Create merchant
          </Button>
        }
      />

      {isLoading ? <MerchantListSkeleton /> : null}

      {isError ? (
        <ErrorState
          title="Unable to load merchants"
          description={errorMessage ?? 'Request failed. Check your connection and try again.'}
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              Retry
            </Button>
          }
        />
      ) : null}

      {!isLoading && !isError && merchants.length === 0 ? (
        <EmptyState
          title="No merchants yet"
          description="Create a merchant account to unlock payments, API keys, webhooks, and analytics for that sandbox workspace."
          icon={<Building2 className="h-5 w-5" />}
          action={
            <Button onClick={() => navigate('/merchants/new')}>
              <Plus className="h-4 w-4" aria-hidden />
              Create merchant
            </Button>
          }
        />
      ) : null}

      {!isLoading && !isError && merchants.length > 0 ? (
        <ul className="grid gap-4 sm:grid-cols-2" data-testid="merchant-list">
          {merchants.map((merchant) => {
            const selected = merchant.id === selectedMerchantId;
            return (
              <li key={merchant.id}>
                <Card className="flex h-full flex-col gap-3" as="article">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 space-y-1">
                      <h2 className="truncate text-base font-semibold text-pf-ink">{merchant.name}</h2>
                      <p className="line-clamp-2 text-sm text-pf-muted">
                        {merchant.description?.trim() || 'No description'}
                      </p>
                    </div>
                    <MerchantStatusBadge status={merchant.status} />
                  </div>

                  <div className="mt-auto flex flex-wrap items-center gap-2 pt-1">
                    {selected ? <Badge tone="info">Selected</Badge> : null}
                    {!selected ? (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => selectMerchant(merchant.id)}
                      >
                        Select
                      </Button>
                    ) : null}
                    <Link
                      to={`/merchants/${merchant.id}/edit`}
                      className="inline-flex"
                    >
                      <Button size="sm" variant="ghost">
                        <Pencil className="h-4 w-4" aria-hidden />
                        Edit
                      </Button>
                    </Link>
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}

function MerchantListSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2" aria-busy="true" aria-label="Loading merchants">
      {[0, 1, 2].map((key) => (
        <Card key={key} className="space-y-3">
          <Skeleton className="h-5 w-2/3" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-8 w-24" />
        </Card>
      ))}
    </div>
  );
}
