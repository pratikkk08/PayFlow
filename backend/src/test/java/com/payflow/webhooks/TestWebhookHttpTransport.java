package com.payflow.webhooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.payflow.webhooks.delivery.WebhookDestination;
import com.payflow.webhooks.delivery.WebhookDeliveryException;
import com.payflow.webhooks.delivery.WebhookDeliveryFailureKind;
import com.payflow.webhooks.delivery.WebhookHttpRequest;
import com.payflow.webhooks.delivery.WebhookHttpResponse;
import com.payflow.webhooks.delivery.WebhookHttpTransport;

/**
 * In-process webhook transport for tests — never opens real network connections.
 */
public class TestWebhookHttpTransport implements WebhookHttpTransport {

	public record Call(WebhookHttpRequest request, WebhookDestination destination) {
	}

	private final List<Call> calls = new ArrayList<>();
	private final AtomicInteger index = new AtomicInteger();
	private Function<Call, WebhookHttpResponse> handler = call -> new WebhookHttpResponse(200, 0);

	public void setHandler(Function<Call, WebhookHttpResponse> handler) {
		this.handler = handler;
		index.set(0);
		calls.clear();
	}

	public void respondWithStatuses(int... statuses) {
		AtomicInteger i = new AtomicInteger();
		setHandler(call -> {
			int idx = Math.min(i.getAndIncrement(), statuses.length - 1);
			return new WebhookHttpResponse(statuses[idx], 0);
		});
	}

	public void failWith(WebhookDeliveryFailureKind kind, String message, Integer status) {
		setHandler(call -> {
			throw new WebhookDeliveryException(kind, message, status);
		});
	}

	public List<Call> calls() {
		return List.copyOf(calls);
	}

	@Override
	public WebhookHttpResponse send(WebhookHttpRequest request, WebhookDestination destination) {
		Call call = new Call(request, destination);
		calls.add(call);
		return handler.apply(call);
	}
}
