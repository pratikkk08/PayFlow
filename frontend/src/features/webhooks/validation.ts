import { WEBHOOK_EVENT_TYPES, type WebhookEventType } from '@/features/webhooks/eventTypes';

const BLOCKED_HOSTNAMES = new Set([
  'localhost',
  'metadata.google.internal',
  'metadata.goog',
  'kubernetes.default',
  'kubernetes.default.svc',
]);

export type WebhookFormValues = {
  url: string;
  eventTypes: string[];
  enabled: boolean;
};

export type WebhookFormErrors = Partial<Record<'url' | 'eventTypes', string>>;

export type WebhookFormResult =
  | { ok: true; url: string; eventTypes: WebhookEventType[]; enabled: boolean }
  | { ok: false; errors: WebhookFormErrors };

function isDottedDecimal(host: string): boolean {
  const parts = host.split('.');
  if (parts.length !== 4) {
    return false;
  }
  return parts.every((part) => {
    if (!/^\d{1,3}$/.test(part)) {
      return false;
    }
    const value = Number(part);
    return value >= 0 && value <= 255;
  });
}

function isIpLiteral(host: string): boolean {
  if (host.startsWith('[') && host.endsWith(']')) {
    return true;
  }
  if (isDottedDecimal(host)) {
    return true;
  }
  return host.includes(':');
}

function isBlockedHostname(host: string): boolean {
  let normalized = host.trim().toLowerCase();
  if (normalized.endsWith('.')) {
    normalized = normalized.slice(0, -1);
  }
  if (BLOCKED_HOSTNAMES.has(normalized)) {
    return true;
  }
  return (
    normalized.endsWith('.localhost') ||
    normalized.endsWith('.local') ||
    normalized.endsWith('.internal') ||
    normalized.endsWith('.intranet')
  );
}

/**
 * Client-side HTTPS URL checks aligned with registration rules.
 * Does not perform DNS and does not bypass server-side SSRF guards.
 */
export function validateWebhookUrl(rawUrl: string): { ok: true; url: string } | { ok: false; error: string } {
  const trimmed = rawUrl.trim();
  if (!trimmed) {
    return { ok: false, error: 'Webhook URL is required' };
  }
  if (trimmed.length > 2048) {
    return { ok: false, error: 'Webhook URL must be at most 2048 characters' };
  }

  let uri: URL;
  try {
    uri = new URL(trimmed);
  } catch {
    return { ok: false, error: 'Webhook URL is malformed' };
  }

  if (uri.protocol !== 'https:') {
    return { ok: false, error: 'Webhook URL must use HTTPS' };
  }
  if (uri.username || uri.password) {
    return { ok: false, error: 'Webhook URL must not contain credentials' };
  }
  if (uri.hash) {
    return { ok: false, error: 'Webhook URL must not contain a fragment' };
  }
  if (!uri.hostname) {
    return { ok: false, error: 'Webhook URL must include a host' };
  }

  const host = uri.hostname.toLowerCase();
  if (isBlockedHostname(host)) {
    return { ok: false, error: 'Webhook URL host is not allowed' };
  }
  if (isIpLiteral(host)) {
    return { ok: false, error: 'Webhook URL must not use an IP literal' };
  }

  return { ok: true, url: trimmed };
}

export function validateWebhookForm(values: WebhookFormValues): WebhookFormResult {
  const errors: WebhookFormErrors = {};
  const urlResult = validateWebhookUrl(values.url);
  if (!urlResult.ok) {
    errors.url = urlResult.error;
  }

  const selected = [...new Set(values.eventTypes)].filter((t) =>
    (WEBHOOK_EVENT_TYPES as readonly string[]).includes(t),
  ) as WebhookEventType[];

  if (selected.length === 0) {
    errors.eventTypes = 'Select at least one supported event type';
  }

  if (errors.url || errors.eventTypes) {
    return { ok: false, errors };
  }

  return {
    ok: true,
    url: urlResult.ok ? urlResult.url : values.url.trim(),
    eventTypes: selected,
    enabled: values.enabled,
  };
}
