import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { KeyRound } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthContext';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { formatTimestamp } from '@/features/payments/formatTimestamp';
import { createApiKey, listApiKeys, revokeApiKey } from '@/features/settings/apiKeyApi';
import { mapApiKeyError } from '@/features/settings/apiKeyErrors';
import { apiKeyKeys } from '@/features/settings/apiKeyKeys';
import { validateCreateApiKeyForm } from '@/features/settings/validation';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { PageHeader } from '@/components/ui/PageHeader';
import { SecretRevealDialog } from '@/components/ui/SecretRevealDialog';
import { Skeleton } from '@/components/ui/Skeleton';
import type { ApiKeyMetadataResponse, ApiKeyType } from '@/types/api';

export function ApiKeysPage() {
  const queryClient = useQueryClient();
  const { status: authStatus, user } = useAuth();
  const {
    selectedMerchant,
    selectedMerchantId,
    merchants,
    isLoading: merchantsLoading,
    isError: merchantsError,
    errorMessage: merchantsErrorMessage,
    refetch: refetchMerchants,
  } = useMerchantSelection();

  const [name, setName] = useState('');
  const [keyType, setKeyType] = useState<ApiKeyType>('SECRET');
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [creating, setCreating] = useState(false);
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [revealedMeta, setRevealedMeta] = useState<{
    name: string;
    type: ApiKeyType;
    prefix: string;
  } | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<ApiKeyMetadataResponse | null>(null);
  const [revoking, setRevoking] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const isOwner =
    Boolean(user?.id) &&
    Boolean(selectedMerchant) &&
    selectedMerchant!.ownerUserId === user!.id;

  const keysQuery = useQuery({
    queryKey: apiKeyKeys.list(selectedMerchantId ?? 'none'),
    queryFn: () => listApiKeys(selectedMerchantId!),
    enabled: Boolean(selectedMerchantId && isOwner),
  });

  useEffect(() => {
    setRevealedSecret(null);
    setRevealedMeta(null);
    setRevokeTarget(null);
    setActionError(null);
    setFormError(null);
  }, [selectedMerchantId]);

  useEffect(() => {
    if (authStatus !== 'authenticated') {
      setRevealedSecret(null);
      setRevealedMeta(null);
    }
  }, [authStatus]);

  function clearRevealedSecret() {
    setRevealedSecret(null);
    setRevealedMeta(null);
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!selectedMerchantId || !isOwner) {
      return;
    }
    const validated = validateCreateApiKeyForm({ name, type: keyType });
    if (!validated.ok) {
      setFieldError(validated.errors.name);
      return;
    }
    setFieldError(undefined);
    setFormError(null);
    setActionError(null);
    setCreating(true);
    try {
      const created = await createApiKey(selectedMerchantId, {
        name: validated.name,
        type: validated.type,
      });
      // Keep plaintext only in component state for the reveal dialog — never query cache.
      setRevealedSecret(created.secret);
      setRevealedMeta({
        name: created.name,
        type: created.type,
        prefix: created.keyPrefix,
      });
      setName('');
      setKeyType('SECRET');
      await queryClient.invalidateQueries({ queryKey: apiKeyKeys.list(selectedMerchantId) });
    } catch (error) {
      setFormError(mapApiKeyError(error, 'Unable to create API key.'));
    } finally {
      setCreating(false);
    }
  }

  async function confirmRevoke() {
    if (!selectedMerchantId || !revokeTarget) {
      return;
    }
    setRevoking(true);
    setActionError(null);
    try {
      await revokeApiKey(selectedMerchantId, revokeTarget.id);
      setRevokeTarget(null);
      await queryClient.invalidateQueries({ queryKey: apiKeyKeys.list(selectedMerchantId) });
    } catch (error) {
      setActionError(mapApiKeyError(error, 'Unable to revoke API key.'));
      setRevokeTarget(null);
    } finally {
      setRevoking(false);
    }
  }

  if (merchantsLoading) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Loading API keys">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (merchantsError) {
    return (
      <ErrorState
        title="Unable to load merchants"
        description={merchantsErrorMessage ?? 'Request failed.'}
        action={
          <Button variant="secondary" onClick={() => refetchMerchants()}>
            Retry
          </Button>
        }
      />
    );
  }

  if (merchants.length === 0 || !selectedMerchantId || !selectedMerchant) {
    return (
      <div>
        <PageHeader
          title="API Keys"
          description="Create and revoke sandbox API keys for a merchant you own."
        />
        <EmptyState
          title="No merchant selected"
          description="Create a merchant first, then manage its TEST API keys."
          icon={<KeyRound className="h-5 w-5" />}
          action={
            <Link to="/merchants/new">
              <Button>Create merchant</Button>
            </Link>
          }
        />
      </div>
    );
  }

  if (!isOwner) {
    return (
      <div>
        <PageHeader
          title="API Keys"
          description="API key management requires merchant OWNER access."
        />
        <ErrorState
          title="Owner access required"
          description="Only the merchant owner can create or revoke API keys. Backend authorization remains authoritative."
        />
      </div>
    );
  }

  const keys = keysQuery.data ?? [];

  return (
    <div data-testid="api-keys-page">
      <PageHeader
        title="API Keys"
        description={`Sandbox TEST keys for ${selectedMerchant.name}. Secrets are shown once at creation and cannot be retrieved later. LIVE keys are not issued.`}
      />

      <Card className="mb-6 space-y-3 border-pf-warning/30 bg-pf-warning-soft/40">
        <p className="text-sm text-pf-ink">
          <span className="font-semibold">Secret keys</span> (sk_test_…) authenticate API calls.{' '}
          <span className="font-semibold">Publishable keys</span> (pk_test_…) are not authentication
          credentials and cannot call privileged endpoints.
        </p>
        <p className="text-sm text-pf-muted">
          PayFlow supports simulated/test integrations only. Never use these keys with real card
          networks or live fund processors.
        </p>
      </Card>

      <Card className="mb-6">
        <h2 className="text-base font-semibold text-pf-ink">Create API key</h2>
        <form className="mt-4 space-y-4" onSubmit={handleCreate} autoComplete="off">
          <Input
            label="Key name"
            name="api-key-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoComplete="off"
            spellCheck={false}
            error={fieldError}
            hint="2–120 characters. Shown in the key list."
          />
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-pf-ink-soft">Key type</legend>
            <div className="flex flex-wrap gap-4 text-sm">
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="api-key-type"
                  value="SECRET"
                  checked={keyType === 'SECRET'}
                  onChange={() => setKeyType('SECRET')}
                />
                Secret (sk_test_)
              </label>
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="api-key-type"
                  value="PUBLISHABLE"
                  checked={keyType === 'PUBLISHABLE'}
                  onChange={() => setKeyType('PUBLISHABLE')}
                />
                Publishable (pk_test_)
              </label>
            </div>
          </fieldset>
          {formError ? (
            <p className="text-sm text-pf-danger" role="alert" data-testid="api-key-form-error">
              {formError}
            </p>
          ) : null}
          <Button type="submit" disabled={creating} data-testid="create-api-key">
            {creating ? 'Creating…' : 'Create TEST key'}
          </Button>
        </form>
      </Card>

      {actionError ? (
        <p className="mb-4 text-sm text-pf-danger" role="alert" data-testid="api-key-action-error">
          {actionError}
        </p>
      ) : null}

      {keysQuery.isLoading ? (
        <div className="space-y-3" aria-busy="true">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : null}

      {keysQuery.isError ? (
        <ErrorState
          title="Unable to load API keys"
          description={mapApiKeyError(keysQuery.error, 'Request failed.')}
          action={
            <Button variant="secondary" onClick={() => keysQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : null}

      {keysQuery.isSuccess && keys.length === 0 ? (
        <EmptyState
          title="No API keys yet"
          description="Create a TEST secret or publishable key for this merchant. The full secret is shown only once."
          icon={<KeyRound className="h-5 w-5" />}
        />
      ) : null}

      {keysQuery.isSuccess && keys.length > 0 ? (
        <div className="overflow-x-auto rounded-pf border border-pf-border" data-testid="api-keys-table">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-pf-muted">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Type</th>
                <th className="px-4 py-3 font-medium">Mode</th>
                <th className="px-4 py-3 font-medium">Prefix</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Created</th>
                <th className="px-4 py-3 font-medium">Last used</th>
                <th className="px-4 py-3 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {keys.map((key) => {
                const active = !key.revokedAt;
                return (
                  <tr key={key.id} className="border-t border-pf-border" data-testid={`api-key-row-${key.id}`}>
                    <td className="px-4 py-3 font-medium text-pf-ink">{key.name}</td>
                    <td className="px-4 py-3 text-pf-ink-soft">{key.type}</td>
                    <td className="px-4 py-3 text-pf-ink-soft">{key.mode}</td>
                    <td className="px-4 py-3 font-mono text-xs text-pf-muted">{key.keyPrefix}…</td>
                    <td className="px-4 py-3">
                      <Badge tone={active ? 'success' : 'danger'}>
                        {active ? 'Active' : 'Revoked'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 text-pf-muted">{formatTimestamp(key.createdAt)}</td>
                    <td className="px-4 py-3 text-pf-muted">{formatTimestamp(key.lastUsedAt)}</td>
                    <td className="px-4 py-3">
                      {active ? (
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => setRevokeTarget(key)}
                          data-testid={`revoke-api-key-${key.id}`}
                        >
                          Revoke
                        </Button>
                      ) : (
                        <span className="text-xs text-pf-muted">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : null}

      <SecretRevealDialog
        open={Boolean(revealedSecret)}
        title="API key created"
        description={
          revealedMeta
            ? `Key “${revealedMeta.name}” (${revealedMeta.type}) was created. Prefix ${revealedMeta.prefix}. Copy the secret now — PayFlow will not show it again.`
            : 'Copy the secret now — PayFlow will not show it again.'
        }
        secret={revealedSecret ?? ''}
        secretLabel={revealedMeta?.type === 'PUBLISHABLE' ? 'Publishable key' : 'Secret key'}
        warning="This value cannot be retrieved later. Store it in a secure secret manager. Closing this dialog clears it from memory."
        onClose={clearRevealedSecret}
        footerNote={
          revealedMeta?.type === 'PUBLISHABLE'
            ? 'Publishable keys are not authentication credentials and cannot authorize privileged API calls.'
            : 'Treat this secret like a password. Rotate by revoking and creating a new key if it leaks.'
        }
      />

      <ConfirmDialog
        open={Boolean(revokeTarget)}
        title="Revoke API key?"
        description={
          revokeTarget
            ? `Revoke “${revokeTarget.name}” (${revokeTarget.keyPrefix}…)? Active requests using this key will fail. This cannot be undone.`
            : ''
        }
        confirmLabel="Revoke key"
        tone="danger"
        confirming={revoking}
        onConfirm={() => {
          void confirmRevoke();
        }}
        onCancel={() => setRevokeTarget(null)}
      />
    </div>
  );
}
