package com.payflow.webhooks.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Connects only to {@link WebhookDestination#address()}, sends HTTP with the original hostname
 * in {@code Host} and TLS SNI, and keeps the JVM PKIX trust store. Redirects are rejected.
 */
@Component
@ConditionalOnProperty(name = "payflow.webhooks.delivery.enabled", havingValue = "true")
@ConditionalOnMissingBean(WebhookHttpTransport.class)
public class PinnedHttpsWebhookTransport implements WebhookHttpTransport {

	private final WebhookDeliveryProperties properties;
	private final SSLSocketFactory sslSocketFactory;

	public PinnedHttpsWebhookTransport(WebhookDeliveryProperties properties) {
		this(properties, HttpsURLConnection.getDefaultSSLSocketFactory());
	}

	/**
	 * Test seam for a PKIX factory that trusts a fixture CA. Production uses the JVM default
	 * factory and never a trust-all manager.
	 */
	PinnedHttpsWebhookTransport(WebhookDeliveryProperties properties, SSLSocketFactory sslSocketFactory) {
		this.properties = properties;
		this.sslSocketFactory = sslSocketFactory;
	}

	@Override
	public WebhookHttpResponse send(WebhookHttpRequest request, WebhookDestination destination) {
		ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "webhook-http");
			t.setDaemon(true);
			return t;
		});
		try {
			Future<WebhookHttpResponse> future = executor.submit(() -> doSend(request, destination));
			return future.get(properties.getOverallTimeoutMs(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.RETRYABLE, "overall_timeout", null, ex);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause() == null ? ex : ex.getCause();
			if (cause instanceof WebhookDeliveryException deliveryEx) {
				throw deliveryEx;
			}
			if (cause instanceof IOException io) {
				throw new WebhookDeliveryException(WebhookDeliveryFailureKind.RETRYABLE, "io_error", null, io);
			}
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.RETRYABLE, "transport_error", null, cause);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.RETRYABLE, "interrupted", null, ex);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private WebhookHttpResponse doSend(WebhookHttpRequest request, WebhookDestination destination) throws IOException {
		byte[] bodyBytes = request.body().getBytes(StandardCharsets.UTF_8);
		try (Socket tcp = new Socket()) {
			tcp.connect(new InetSocketAddress(destination.address(), destination.port()),
					(int) properties.getConnectTimeoutMs());
			tcp.setSoTimeout((int) properties.getReadTimeoutMs());
			SSLSocket ssl = (SSLSocket) new SniSslSocketFactory(sslSocketFactory, destination.hostname())
					.createSocket(tcp, destination.hostname(), destination.port(), true);
			try (ssl) {
				ssl.setSoTimeout((int) properties.getReadTimeoutMs());
				ssl.startHandshake();
				OutputStream out = ssl.getOutputStream();
				out.write(requestBytes(destination, request.headers(), bodyBytes));
				out.flush();
				return readResponse(ssl.getInputStream());
			}
		}
	}

	private byte[] requestBytes(WebhookDestination destination, Map<String, String> headers, byte[] body)
			throws IOException {
		String path = destination.pathAndQuery();
		rejectCrlf(path);
		rejectCrlf(destination.hostname());
		String host = destination.port() == 443
				? destination.hostname()
				: destination.hostname() + ":" + destination.port();
		StringBuilder text = new StringBuilder();
		text.append("POST ").append(path).append(" HTTP/1.1\r\n");
		text.append("Host: ").append(host).append("\r\n");
		text.append("Content-Type: application/json; charset=utf-8\r\n");
		text.append("Accept: application/json\r\n");
		text.append("Content-Length: ").append(body.length).append("\r\n");
		text.append("Connection: close\r\n");
		for (Map.Entry<String, String> header : headers.entrySet()) {
			if (isHopByHop(header.getKey())) {
				continue;
			}
			rejectCrlf(header.getKey());
			rejectCrlf(header.getValue());
			text.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
		}
		text.append("\r\n");
		byte[] head = text.toString().getBytes(StandardCharsets.US_ASCII);
		byte[] message = new byte[head.length + body.length];
		System.arraycopy(head, 0, message, 0, head.length);
		System.arraycopy(body, 0, message, head.length, body.length);
		return message;
	}

	private WebhookHttpResponse readResponse(InputStream in) throws IOException {
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		int matched = 0;
		byte[] marker = new byte[] { '\r', '\n', '\r', '\n' };
		while (matched < 4) {
			int value = in.read();
			if (value < 0) {
				throw new IOException("truncated_http_response");
			}
			header.write(value);
			if (header.size() > 8192) {
				throw new IOException("response_headers_too_large");
			}
			matched = value == marker[matched] ? matched + 1 : (value == '\r' ? 1 : 0);
		}
		String head = header.toString(StandardCharsets.ISO_8859_1);
		int status = parseStatus(head);
		if (status >= 300 && status < 400) {
			throw new WebhookDeliveryException(WebhookDeliveryFailureKind.PERMANENT, "redirect_rejected", status);
		}
		int read = 0;
		byte[] buf = new byte[1024];
		int n;
		while ((n = in.read(buf)) >= 0) {
			read += n;
			if (read > properties.getMaxResponseBytes()) {
				break;
			}
		}
		return new WebhookHttpResponse(status, read);
	}

	private static int parseStatus(String head) throws IOException {
		int lineEnd = head.indexOf("\r\n");
		String statusLine = lineEnd < 0 ? head : head.substring(0, lineEnd);
		String[] parts = statusLine.split(" ");
		if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
			throw new IOException("malformed_status_line");
		}
		try {
			return Integer.parseInt(parts[1]);
		}
		catch (NumberFormatException ex) {
			throw new IOException("malformed_status_line", ex);
		}
	}

	private static void rejectCrlf(String value) throws IOException {
		if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
			throw new IOException("illegal_header");
		}
	}

	private static boolean isHopByHop(String name) {
		String normalized = name.toLowerCase(java.util.Locale.ROOT);
		return normalized.equals("host") || normalized.equals("content-length") || normalized.equals("connection")
				|| normalized.equals("transfer-encoding");
	}

	/**
	 * Optional low-level pin check used by tests: ensures sockets can be opened to the IP.
	 */
	static Callable<Boolean> probeConnect(InetAddress address, int port, int timeoutMs) {
		return () -> {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(address, port), timeoutMs);
				return socket.isConnected() && !(socket instanceof SSLSocket);
			}
		};
	}
}
