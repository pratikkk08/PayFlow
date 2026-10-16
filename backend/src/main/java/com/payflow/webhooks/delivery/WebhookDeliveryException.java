package com.payflow.webhooks.delivery;

public class WebhookDeliveryException extends RuntimeException {

	private final WebhookDeliveryFailureKind kind;
	private final Integer httpStatus;

	public WebhookDeliveryException(WebhookDeliveryFailureKind kind, String message, Integer httpStatus) {
		super(message);
		this.kind = kind;
		this.httpStatus = httpStatus;
	}

	public WebhookDeliveryException(WebhookDeliveryFailureKind kind, String message, Integer httpStatus,
			Throwable cause) {
		super(message, cause);
		this.kind = kind;
		this.httpStatus = httpStatus;
	}

	public WebhookDeliveryFailureKind getKind() {
		return kind;
	}

	public Integer getHttpStatus() {
		return httpStatus;
	}
}
