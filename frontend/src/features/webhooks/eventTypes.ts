/** Wire values from backend OutboxEventType — keep in sync with the API. */
export const WEBHOOK_EVENT_TYPES = [
  'payment.created',
  'payment.authorized',
  'payment.captured',
  'payment.cancelled',
  'payment.failed',
  'refund.succeeded',
  'refund.failed',
] as const;

export type WebhookEventType = (typeof WEBHOOK_EVENT_TYPES)[number];

export function isSupportedWebhookEventType(value: string): value is WebhookEventType {
  return (WEBHOOK_EVENT_TYPES as readonly string[]).includes(value);
}
