import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Webhook } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthContext';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { formatTimestamp } from '@/features/payments/formatTimestamp';
import { WEBHOOK_EVENT_TYPES } from '@/features/webhooks/eventTypes';
import {
  createWebhook,
  deleteWebhook,
  listWebhooks,
  updateWebhook,
} from '@/features/webhooks/webhookApi';
import { mapWebhookError } from '@/features/webhooks/webhookErrors';
import { webhookKeys } from '@/features/webhooks/webhookKeys';
import { validateWebhookForm } from '@/features/webhooks/validation';
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
import type { WebhookEndpointResponse } from '@/types/api';

type EditState = {
  id: string;
  url: string;
  eventTypes: string[];
  enabled: boolean;
};

export function WebhooksPage() {
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

  const [url, setUrl] = useState('');
  const [eventTypes, setEventTypes] = useState<string[]>(['payment.captured']);
  const [enabled, setEnabled] = useState(true);
  const [fieldErrors, setFieldErrors] = useState<{ url?: string; eventTypes?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [revealedUrl, setRevealedUrl] = useState<string | null>(null);
  const [edit, setEdit] = useState<EditState | null>(null);
  const [editErrors, setEditErrors] = useState<{ url?: string; eventTypes?: string }>({});
  const [editError, setEditError] = useState<string | null>(null);
  const [savingEdit, setSavingEdit] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<WebhookEndpointResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const isOwner =
    Boolean(user?.id) &&
    Boolean(selectedMerchant) &&
    selectedMerchant!.ownerUserId === user!.id;

  const webhooksQuery = useQuery({
    queryKey: webhookKeys.list(selectedMerchantId ?? 'none'),
    queryFn: () => listWebhooks(selectedMerchantId!),
    enabled: Boolean(selectedMerchantId && isOwner),
  });

  useEffect(() => {
    setRevealedSecret(null);
    setRevealedUrl(null);
    setDeleteTarget(null);
    setEdit(null);
    setActionError(null);
    setFormError(null);
    setEditError(null);
  }, [selectedMerchantId]);

  useEffect(() => {
    if (authStatus !== 'authenticated') {
      setRevealedSecret(null);
      setRevealedUrl(null);
    }
  }, [authStatus]);

  function clearRevealedSecret() {
    setRevealedSecret(null);
    setRevealedUrl(null);
  }

  function toggleEvent(
    current: string[],
    value: string,
    setter: (next: string[]) => void,
  ) {
    if (current.includes(value)) {
      setter(current.filter((item) => item !== value));
    } else {
      setter([...current, value]);
    }
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!selectedMerchantId || !isOwner) {
      return;
    }
    const validated = validateWebhookForm({ url, eventTypes, enabled });
    if (!validated.ok) {
      setFieldErrors(validated.errors);
      return;
    }
    setFieldErrors({});
    setFormError(null);
    setActionError(null);
    setCreating(true);
    try {
      const created = await createWebhook(selectedMerchantId, {
        url: validated.url,
        eventTypes: validated.eventTypes,
        enabled: validated.enabled,
      });
      setRevealedSecret(created.signingSecret);
      setRevealedUrl(created.url);
      setUrl('');
      setEventTypes(['payment.captured']);
      setEnabled(true);
      await queryClient.invalidateQueries({ queryKey: webhookKeys.list(selectedMerchantId) });
    } catch (error) {
      setFormError(mapWebhookError(error, 'Unable to create webhook endpoint.'));
    } finally {
      setCreating(false);
    }
  }

  function startEdit(endpoint: WebhookEndpointResponse) {
    setEdit({
      id: endpoint.id,
      url: endpoint.url,
      eventTypes: [...endpoint.eventTypes],
      enabled: endpoint.enabled,
    });
    setEditErrors({});
    setEditError(null);
  }

  async function handleSaveEdit(event: FormEvent) {
    event.preventDefault();
    if (!selectedMerchantId || !edit) {
      return;
    }
    const validated = validateWebhookForm({
      url: edit.url,
      eventTypes: edit.eventTypes,
      enabled: edit.enabled,
    });
    if (!validated.ok) {
      setEditErrors(validated.errors);
      return;
    }
    setEditErrors({});
    setEditError(null);
    setSavingEdit(true);
    try {
      await updateWebhook(selectedMerchantId, edit.id, {
        url: validated.url,
        eventTypes: validated.eventTypes,
        enabled: validated.enabled,
      });
      setEdit(null);
      await queryClient.invalidateQueries({ queryKey: webhookKeys.list(selectedMerchantId) });
    } catch (error) {
      setEditError(mapWebhookError(error, 'Unable to update webhook endpoint.'));
    } finally {
      setSavingEdit(false);
    }
  }

  async function toggleEnabled(endpoint: WebhookEndpointResponse) {
    if (!selectedMerchantId) {
      return;
    }
    setTogglingId(endpoint.id);
    setActionError(null);
    try {
      await updateWebhook(selectedMerchantId, endpoint.id, { enabled: !endpoint.enabled });
      await queryClient.invalidateQueries({ queryKey: webhookKeys.list(selectedMerchantId) });
    } catch (error) {
      setActionError(mapWebhookError(error, 'Unable to update webhook endpoint.'));
    } finally {
      setTogglingId(null);
    }
  }

  async function confirmDelete() {
    if (!selectedMerchantId || !deleteTarget) {
      return;
    }
    setDeleting(true);
    setActionError(null);
    try {
      await deleteWebhook(selectedMerchantId, deleteTarget.id);
      setDeleteTarget(null);
      await queryClient.invalidateQueries({ queryKey: webhookKeys.list(selectedMerchantId) });
    } catch (error) {
      setActionError(mapWebhookError(error, 'Unable to delete webhook endpoint.'));
      setDeleteTarget(null);
    } finally {
      setDeleting(false);
    }
  }

  if (merchantsLoading) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Loading webhooks">
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
          title="Webhooks"
          description="Register HTTPS endpoints for sandbox event notifications."
        />
        <EmptyState
          title="No merchant selected"
          description="Create a merchant first, then register webhook endpoints."
          icon={<Webhook className="h-5 w-5" />}
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
          title="Webhooks"
          description="Webhook management requires merchant OWNER access."
        />
        <ErrorState
          title="Owner access required"
          description="Only the merchant owner can manage webhook endpoints. Backend authorization remains authoritative."
        />
      </div>
    );
  }

  const endpoints = webhooksQuery.data ?? [];

  return (
    <div data-testid="webhooks-page">
      <PageHeader
        title="Webhooks"
        description={`Sandbox webhook endpoints for ${selectedMerchant.name}. Signing secrets are shown once at creation. Outbound delivery may be disabled in local development.`}
      />

      <Card className="mb-6 space-y-2 border-pf-warning/30 bg-pf-warning-soft/40">
        <p className="text-sm text-pf-ink">
          PayFlow webhooks are <strong className="font-semibold">sandbox notifications</strong> for
          simulated payment and refund events. They are not live payment processor callbacks.
        </p>
        <p className="text-sm text-pf-muted">
          Use a public HTTPS URL you control. Localhost, private, and IP-literal destinations are
          rejected. Server-side SSRF checks remain authoritative. Secret rotation is not available
          in the API yet — delete and recreate if a secret leaks. Delivery history APIs are not
          exposed.
        </p>
      </Card>

      <Card className="mb-6">
        <h2 className="text-base font-semibold text-pf-ink">Register endpoint</h2>
        <form className="mt-4 space-y-4" onSubmit={handleCreate} autoComplete="off">
          <Input
            label="HTTPS URL"
            name="webhook-url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            autoComplete="off"
            spellCheck={false}
            placeholder="https://example.com/payflow/hooks"
            error={fieldErrors.url}
            hint="HTTPS only. No credentials, fragments, localhost, or IP literals."
          />
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-pf-ink-soft">Event subscriptions</legend>
            <div className="grid gap-2 sm:grid-cols-2" data-testid="create-event-types">
              {WEBHOOK_EVENT_TYPES.map((type) => (
                <label key={type} className="inline-flex items-center gap-2 text-sm text-pf-ink">
                  <input
                    type="checkbox"
                    checked={eventTypes.includes(type)}
                    onChange={() => toggleEvent(eventTypes, type, setEventTypes)}
                  />
                  <span className="font-mono text-xs">{type}</span>
                </label>
              ))}
            </div>
            {fieldErrors.eventTypes ? (
              <p className="text-xs text-pf-danger" role="alert">
                {fieldErrors.eventTypes}
              </p>
            ) : null}
          </fieldset>
          <label className="inline-flex items-center gap-2 text-sm text-pf-ink">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            Enabled after creation
          </label>
          {formError ? (
            <p className="text-sm text-pf-danger" role="alert" data-testid="webhook-form-error">
              {formError}
            </p>
          ) : null}
          <Button type="submit" disabled={creating} data-testid="create-webhook">
            {creating ? 'Creating…' : 'Create endpoint'}
          </Button>
        </form>
      </Card>

      {actionError ? (
        <p className="mb-4 text-sm text-pf-danger" role="alert" data-testid="webhook-action-error">
          {actionError}
        </p>
      ) : null}

      {webhooksQuery.isLoading ? (
        <div className="space-y-3" aria-busy="true">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : null}

      {webhooksQuery.isError ? (
        <ErrorState
          title="Unable to load webhooks"
          description={mapWebhookError(webhooksQuery.error, 'Request failed.')}
          action={
            <Button variant="secondary" onClick={() => webhooksQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : null}

      {webhooksQuery.isSuccess && endpoints.length === 0 ? (
        <EmptyState
          title="No webhook endpoints"
          description="Register an HTTPS URL to receive sandbox event notifications. The signing secret is shown only once."
          icon={<Webhook className="h-5 w-5" />}
        />
      ) : null}

      {webhooksQuery.isSuccess && endpoints.length > 0 ? (
        <ul className="space-y-4" data-testid="webhooks-list">
          {endpoints.map((endpoint) => (
            <li key={endpoint.id}>
              <Card data-testid={`webhook-row-${endpoint.id}`}>
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="space-y-2 min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-mono text-sm text-pf-ink" title={endpoint.url}>
                        {endpoint.url}
                      </p>
                      <Badge tone={endpoint.enabled ? 'success' : 'neutral'}>
                        {endpoint.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                    </div>
                    <p className="text-xs text-pf-muted">
                      Secret prefix{' '}
                      <span className="font-mono">{endpoint.secretPrefix}…</span>
                    </p>
                    <p className="text-xs text-pf-muted">
                      Events: {endpoint.eventTypes.join(', ') || '—'}
                    </p>
                    <p className="text-xs text-pf-muted">
                      Created {formatTimestamp(endpoint.createdAt)} · Updated{' '}
                      {formatTimestamp(endpoint.updatedAt)}
                      {endpoint.secretRotatedAt
                        ? ` · Secret rotated ${formatTimestamp(endpoint.secretRotatedAt)}`
                        : ''}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2 shrink-0">
                    <Button
                      variant="secondary"
                      size="sm"
                      disabled={togglingId === endpoint.id}
                      onClick={() => {
                        void toggleEnabled(endpoint);
                      }}
                      data-testid={`toggle-webhook-${endpoint.id}`}
                    >
                      {endpoint.enabled ? 'Disable' : 'Enable'}
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => startEdit(endpoint)}
                      data-testid={`edit-webhook-${endpoint.id}`}
                    >
                      Edit
                    </Button>
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => setDeleteTarget(endpoint)}
                      data-testid={`delete-webhook-${endpoint.id}`}
                    >
                      Delete
                    </Button>
                  </div>
                </div>

                {edit?.id === endpoint.id ? (
                  <form
                    className="mt-4 space-y-4 border-t border-pf-border pt-4"
                    onSubmit={handleSaveEdit}
                    autoComplete="off"
                    data-testid={`edit-webhook-form-${endpoint.id}`}
                  >
                    <Input
                      label="HTTPS URL"
                      name={`edit-webhook-url-${endpoint.id}`}
                      value={edit.url}
                      onChange={(e) => setEdit({ ...edit, url: e.target.value })}
                      autoComplete="off"
                      spellCheck={false}
                      error={editErrors.url}
                    />
                    <fieldset className="space-y-2">
                      <legend className="text-sm font-medium text-pf-ink-soft">
                        Event subscriptions
                      </legend>
                      <div className="grid gap-2 sm:grid-cols-2">
                        {WEBHOOK_EVENT_TYPES.map((type) => (
                          <label
                            key={type}
                            className="inline-flex items-center gap-2 text-sm text-pf-ink"
                          >
                            <input
                              type="checkbox"
                              checked={edit.eventTypes.includes(type)}
                              onChange={() =>
                                setEdit({
                                  ...edit,
                                  eventTypes: edit.eventTypes.includes(type)
                                    ? edit.eventTypes.filter((item) => item !== type)
                                    : [...edit.eventTypes, type],
                                })
                              }
                            />
                            <span className="font-mono text-xs">{type}</span>
                          </label>
                        ))}
                      </div>
                      {editErrors.eventTypes ? (
                        <p className="text-xs text-pf-danger" role="alert">
                          {editErrors.eventTypes}
                        </p>
                      ) : null}
                    </fieldset>
                    <label className="inline-flex items-center gap-2 text-sm text-pf-ink">
                      <input
                        type="checkbox"
                        checked={edit.enabled}
                        onChange={(e) => setEdit({ ...edit, enabled: e.target.checked })}
                      />
                      Enabled
                    </label>
                    {editError ? (
                      <p className="text-sm text-pf-danger" role="alert">
                        {editError}
                      </p>
                    ) : null}
                    <div className="flex gap-2">
                      <Button type="submit" disabled={savingEdit} data-testid="save-webhook-edit">
                        {savingEdit ? 'Saving…' : 'Save changes'}
                      </Button>
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => setEdit(null)}
                        disabled={savingEdit}
                      >
                        Cancel
                      </Button>
                    </div>
                  </form>
                ) : null}
              </Card>
            </li>
          ))}
        </ul>
      ) : null}

      <SecretRevealDialog
        open={Boolean(revealedSecret)}
        title="Webhook endpoint created"
        description={
          revealedUrl
            ? `Endpoint ${revealedUrl} was registered. Copy the signing secret now — PayFlow will not show it again.`
            : 'Copy the signing secret now — PayFlow will not show it again.'
        }
        secret={revealedSecret ?? ''}
        secretLabel="Signing secret"
        warning="This signing secret cannot be retrieved later. Closing this dialog clears it from memory. There is no rotation API yet."
        onClose={clearRevealedSecret}
        footerNote="Verify webhook signatures on your receiver. Outbound delivery may be disabled locally — registering an endpoint does not contact external sites."
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Delete webhook endpoint?"
        description={
          deleteTarget
            ? `Delete ${deleteTarget.url}? This removes the endpoint configuration. The signing secret will no longer be valid for new deliveries.`
            : ''
        }
        confirmLabel="Delete endpoint"
        tone="danger"
        confirming={deleting}
        onConfirm={() => {
          void confirmDelete();
        }}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
