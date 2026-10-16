package com.payflow.webhooks.delivery;

public record WebhookHttpResponse(int statusCode, int bodyBytesRead) {
}
