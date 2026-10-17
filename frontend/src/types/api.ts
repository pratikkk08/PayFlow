/** Shared API shapes for auth and account surfaces. */

export type HealthStatus = {
  status: string;
};

export type BrowserAccessTokenResponse = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
};

export type UserResponse = {
  id: string;
  fullName: string;
  email: string;
  roles: string[];
  createdAt: string;
};

export type RegisterRequest = {
  fullName: string;
  email: string;
  password: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type MerchantStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED';

export type MerchantResponse = {
  id: string;
  name: string;
  description: string | null;
  status: MerchantStatus;
  ownerUserId: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateMerchantRequest = {
  name: string;
  description?: string | null;
};

/** Name/description only — ownership and unsupported status transitions are not client-editable. */
export type UpdateMerchantRequest = {
  name?: string;
  description?: string | null;
};

/** Mirrors backend AnalyticsOverviewResponse (INR minor units; UTC window). */
export type AnalyticsOverviewResponse = {
  merchantId: string;
  reportingTimezone: string;
  fromInclusive: string;
  toInclusive: string;
  rangeStartInclusive: string;
  rangeEndExclusive: string;
  currency: string;
  totalPaymentCount: number;
  createdCount: number;
  authorizedCount: number;
  capturedCount: number;
  failedCount: number;
  cancelledCount: number;
  totalCapturedAmountMinor: number;
  totalSucceededRefundAmountMinor: number;
  netCapturedAmountMinor: number;
  /** capturedCount / totalPaymentCount; null when denominator is zero. */
  paymentCaptureRate: number | string | null;
  paymentCaptureRateDefinition: string;
  riskBlockCount: number;
  riskReviewCount: number;
  disclaimer: string;
};

/** Mirrors backend AnalyticsDailyPoint. */
export type AnalyticsDailyPoint = {
  date: string;
  paymentsCreated: number;
  paymentsCaptured: number;
  paymentsFailed: number;
  capturedAmountMinor: number;
  succeededRefundAmountMinor: number;
  netAmountMinor: number;
};

/** Mirrors backend AnalyticsDailyResponse. */
export type AnalyticsDailyResponse = {
  merchantId: string;
  reportingTimezone: string;
  fromInclusive: string;
  toInclusive: string;
  rangeStartInclusive: string;
  rangeEndExclusive: string;
  currency: string;
  days: AnalyticsDailyPoint[];
  disclaimer: string;
};

export type PaymentStatus =
  | 'CREATED'
  | 'AUTHORIZED'
  | 'CAPTURED'
  | 'FAILED'
  | 'CANCELLED';

/** Mirrors backend PaymentResponse (processor_ref is not exposed by the API). */
export type PaymentResponse = {
  paymentId: string;
  merchantId: string;
  merchantReference: string;
  amountMinor: number;
  currency: string;
  status: PaymentStatus;
  failureCode: string | null;
  authorizedAt: string | null;
  capturedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
};

export type CreatePaymentRequest = {
  merchantId: string;
  merchantReference: string;
  amountMinor: number;
  currency: 'INR';
  description?: string | null;
};

export type PaymentPageResponse = {
  content: PaymentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type RefundStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED';

/** Mirrors backend RefundResponse. */
export type RefundResponse = {
  refundId: string;
  paymentId: string;
  merchantId: string;
  merchantReference: string;
  amountMinor: number;
  currency: string;
  status: RefundStatus;
  failureCode: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateRefundRequest = {
  merchantReference: string;
  amountMinor: number;
  reason?: string | null;
};

export type RefundPageResponse = {
  content: RefundResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ApiKeyType = 'SECRET' | 'PUBLISHABLE';

/** Backend models LIVE but only issues TEST keys. */
export type ApiKeyMode = 'TEST' | 'LIVE';

/** List/metadata — never includes raw secret or hash. */
export type ApiKeyMetadataResponse = {
  id: string;
  merchantId: string;
  name: string;
  type: ApiKeyType;
  mode: ApiKeyMode;
  keyPrefix: string;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
};

/** Create response — `secret` returned once; do not cache. */
export type ApiKeyCreatedResponse = {
  id: string;
  merchantId: string;
  name: string;
  type: ApiKeyType;
  mode: ApiKeyMode;
  keyPrefix: string;
  secret: string;
  createdAt: string;
};

export type CreateApiKeyRequest = {
  name: string;
  type?: ApiKeyType;
};

/** List/get — never includes signing secret. */
export type WebhookEndpointResponse = {
  id: string;
  merchantId: string;
  url: string;
  secretPrefix: string;
  enabled: boolean;
  eventTypes: string[];
  createdAt: string;
  updatedAt: string;
  secretRotatedAt: string | null;
  version: number;
};

/** Create response — `signingSecret` returned once; do not cache. */
export type WebhookEndpointCreatedResponse = {
  id: string;
  merchantId: string;
  url: string;
  secretPrefix: string;
  signingSecret: string;
  enabled: boolean;
  eventTypes: string[];
  createdAt: string;
  updatedAt: string;
  secretRotatedAt: string | null;
  version: number;
};

export type CreateWebhookEndpointRequest = {
  url: string;
  eventTypes: string[];
  enabled?: boolean;
};

export type UpdateWebhookEndpointRequest = {
  url?: string;
  eventTypes?: string[];
  enabled?: boolean;
};
