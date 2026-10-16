package com.payflow.webhooks.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Test;

/**
 * Local TLS fixture for {@link PinnedHttpsWebhookTransport}.
 * Loopback is injected only as an already-selected destination. Production
 * {@link com.payflow.webhooks.service.WebhookSsrfGuard} still rejects loopback DNS results.
 * Certificate checks use PKIX against a fixture CA — not a trust-all manager.
 */
class PinnedHttpsWebhookTransportTest {

	private static final String HOST = "hooks.fixture.invalid";
	private static final char[] PASSWORD = "changeit".toCharArray();

	@Test
	void pinnedHttpsValidatesChainSniAndHostname() throws Exception {
		Path dir = Files.createTempDirectory("payflow-tls-ok");
		Material material = Material.signed(dir, HOST);
		try (TlsPeer peer = TlsPeer.start(material.serverKeyStore(), 200)) {
			PinnedHttpsWebhookTransport transport = transport(material.clientFactory(), 2000, 2000, 5000);
			WebhookHttpResponse response = transport.send(request("/hook"), destination(peer.port()));

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(peer.awaitRequest()).contains("POST /hook");
			assertThat(peer.hostHeader().toLowerCase(Locale.ROOT)).startsWith(HOST);
			assertThat(peer.sni()).isEqualTo(HOST);
			assertThat(peer.body()).isEqualTo("{\"ok\":true}");
		}
	}

	@Test
	void untrustedCertificateIsRejected() throws Exception {
		Path dir = Files.createTempDirectory("payflow-tls-bad");
		Material trusted = Material.signed(dir.resolve("trusted"), HOST);
		Material stranger = Material.selfSigned(dir.resolve("stranger"), HOST);
		try (TlsPeer peer = TlsPeer.start(stranger.serverKeyStore(), 200)) {
			PinnedHttpsWebhookTransport transport = transport(trusted.clientFactory(), 2000, 2000, 5000);
			assertThatThrownBy(() -> transport.send(request("/hook"), destination(peer.port())))
					.isInstanceOf(WebhookDeliveryException.class)
					.extracting(ex -> ((WebhookDeliveryException) ex).getKind())
					.isEqualTo(WebhookDeliveryFailureKind.RETRYABLE);
		}
	}

	@Test
	void hostnameMismatchIsRejected() throws Exception {
		Path dir = Files.createTempDirectory("payflow-tls-host");
		Material material = Material.signed(dir, "other.fixture.invalid");
		try (TlsPeer peer = TlsPeer.start(material.serverKeyStore(), 200)) {
			PinnedHttpsWebhookTransport transport = transport(material.clientFactory(), 2000, 2000, 5000);
			assertThatThrownBy(() -> transport.send(request("/hook"), destination(peer.port())))
					.isInstanceOf(WebhookDeliveryException.class)
					.extracting(ex -> ((WebhookDeliveryException) ex).getKind())
					.isEqualTo(WebhookDeliveryFailureKind.RETRYABLE);
		}
	}

	@Test
	void redirectIsRejected() throws Exception {
		Path dir = Files.createTempDirectory("payflow-tls-redir");
		Material material = Material.signed(dir, HOST);
		try (TlsPeer peer = TlsPeer.start(material.serverKeyStore(), 302)) {
			PinnedHttpsWebhookTransport transport = transport(material.clientFactory(), 2000, 2000, 5000);
			assertThatThrownBy(() -> transport.send(request("/hook"), destination(peer.port())))
					.isInstanceOf(WebhookDeliveryException.class)
					.hasMessage("redirect_rejected")
					.extracting(ex -> ((WebhookDeliveryException) ex).getKind())
					.isEqualTo(WebhookDeliveryFailureKind.PERMANENT);
		}
	}

	@Test
	void readTimeoutAndOverallTimeoutFailClosed() throws Exception {
		Path dir = Files.createTempDirectory("payflow-tls-time");
		Material material = Material.signed(dir, HOST);
		try (TlsPeer peer = TlsPeer.start(material.serverKeyStore(), 200)) {
			peer.hangAfterRequest();
			PinnedHttpsWebhookTransport readTimeout = transport(material.clientFactory(), 1000, 300, 5000);
			assertThatThrownBy(() -> readTimeout.send(request("/slow"), destination(peer.port())))
					.isInstanceOf(WebhookDeliveryException.class)
					.hasMessage("io_error");
		}

		try (TlsPeer peer = TlsPeer.start(material.serverKeyStore(), 200)) {
			peer.hangAfterRequest();
			PinnedHttpsWebhookTransport overall = transport(material.clientFactory(), 1000, 5000, 400);
			assertThatThrownBy(() -> overall.send(request("/slow"), destination(peer.port())))
					.isInstanceOf(WebhookDeliveryException.class)
					.hasMessage("overall_timeout")
					.extracting(ex -> ((WebhookDeliveryException) ex).getKind())
					.isEqualTo(WebhookDeliveryFailureKind.RETRYABLE);
		}
	}

	@Test
	void connectionUsesPinnedAddressAndDoesNotFallBack() throws Exception {
		Path dir = Files.createTempDirectory("payflow-tls-pin");
		Material material = Material.signed(dir, HOST);
		try (TlsPeer decoy = TlsPeer.start(material.serverKeyStore(), 200);
				ServerSocket closed = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			int closedPort = closed.getLocalPort();
			closed.close();
			PinnedHttpsWebhookTransport transport = transport(material.clientFactory(), 500, 500, 2000);
			WebhookDestination pinnedClosed = new WebhookDestination(HOST, closedPort, "/hook",
					InetAddress.getByName("127.0.0.1"));
			assertThatThrownBy(() -> transport.send(request("/hook"), pinnedClosed))
					.isInstanceOf(WebhookDeliveryException.class)
					.hasMessage("io_error");
			assertThat(decoy.received()).isZero();
		}
	}

	@Test
	void productionResolverStillRejectsLoopbackDns() {
		WebhookDestinationResolver resolver = new WebhookDestinationResolver(host -> new InetAddress[] {
				InetAddress.getByName("127.0.0.1")
		});
		assertThatThrownBy(() -> resolver.resolve("https://" + HOST + "/hook"))
				.isInstanceOf(WebhookDeliveryException.class)
				.hasMessage("all_resolved_addresses_blocked");
	}

	@Test
	void resolvedAddressIsWhatTheTransportConnectsTo() throws Exception {
		Path dir = Files.createTempDirectory("payflow-tls-toctou");
		Material material = Material.signed(dir, HOST);
		java.util.concurrent.atomic.AtomicReference<InetAddress[]> answers = new java.util.concurrent.atomic.AtomicReference<>(
				new InetAddress[] { InetAddress.getByName("8.8.8.8") });
		AtomicInteger lookups = new AtomicInteger();
		WebhookDnsResolver dns = host -> {
			lookups.incrementAndGet();
			return answers.get();
		};
		WebhookDestinationResolver resolver = new WebhookDestinationResolver(dns);
		WebhookDestination resolved = resolver.resolve("https://" + HOST + "/hook");
		assertThat(resolved.address().getHostAddress()).isEqualTo("8.8.8.8");
		assertThat(lookups.get()).isEqualTo(1);

		answers.set(new InetAddress[] { InetAddress.getByName("127.0.0.1") });
		assertThat(resolved.address().getHostAddress()).isEqualTo("8.8.8.8");

		try (TlsPeer peer = TlsPeer.start(material.serverKeyStore(), 200)) {
			PinnedHttpsWebhookTransport transport = transport(material.clientFactory(), 2000, 2000, 5000);
			assertThat(transport.send(request("/hook"), destination(peer.port())).statusCode()).isEqualTo(200);
			assertThat(lookups.get()).isEqualTo(1);
			assertThat(peer.awaitRequest()).contains("POST /hook");
		}
	}

	private static WebhookHttpRequest request(String path) {
		return new WebhookHttpRequest("https://" + HOST + path, "{\"ok\":true}",
				Map.of("X-PayFlow-Event-Id", "evt-fixture"));
	}

	private static WebhookDestination destination(int port) throws Exception {
		return new WebhookDestination(HOST, port, "/hook", InetAddress.getByName("127.0.0.1"));
	}

	private static PinnedHttpsWebhookTransport transport(javax.net.ssl.SSLSocketFactory factory,
			int connectMs, int readMs, int overallMs) {
		WebhookDeliveryProperties properties = new WebhookDeliveryProperties();
		properties.setConnectTimeoutMs(connectMs);
		properties.setReadTimeoutMs(readMs);
		properties.setOverallTimeoutMs(overallMs);
		properties.setMaxResponseBytes(1024);
		return new PinnedHttpsWebhookTransport(properties, factory);
	}

	private record Material(Path serverKeyStore, javax.net.ssl.SSLSocketFactory clientFactory) {
		static Material signed(Path dir, String hostname) throws Exception {
			Files.createDirectories(dir);
			Path ca = dir.resolve("ca.p12");
			Path caCert = dir.resolve("ca.crt");
			Path server = dir.resolve("server.p12");
			Path csr = dir.resolve("server.csr");
			Path reply = dir.resolve("server.crt");
			Path trust = dir.resolve("trust.p12");
			keytool(dir, "-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
					"-storetype", "PKCS12", "-keystore", ca.toString(), "-storepass", "changeit", "-keypass", "changeit",
					"-dname", "CN=PayFlow Test CA", "-ext", "bc:c");
			keytool(dir, "-exportcert", "-alias", "ca", "-keystore", ca.toString(), "-storepass", "changeit",
					"-rfc", "-file", caCert.toString());
			keytool(dir, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
					"-storetype", "PKCS12", "-keystore", server.toString(), "-storepass", "changeit", "-keypass",
					"changeit", "-dname", "CN=" + hostname, "-ext", "SAN=dns:" + hostname);
			keytool(dir, "-certreq", "-alias", "server", "-keystore", server.toString(), "-storepass", "changeit",
					"-file", csr.toString());
			keytool(dir, "-gencert", "-alias", "ca", "-keystore", ca.toString(), "-storepass", "changeit",
					"-infile", csr.toString(), "-outfile", reply.toString(), "-rfc", "-validity", "2",
					"-ext", "SAN=dns:" + hostname);
			keytool(dir, "-importcert", "-alias", "ca", "-keystore", server.toString(), "-storepass", "changeit",
					"-file", caCert.toString(), "-noprompt");
			keytool(dir, "-importcert", "-alias", "server", "-keystore", server.toString(), "-storepass", "changeit",
					"-file", reply.toString(), "-noprompt");
			keytool(dir, "-importcert", "-alias", "ca", "-keystore", trust.toString(), "-storetype", "PKCS12",
					"-storepass", "changeit", "-file", caCert.toString(), "-noprompt");
			return new Material(server, clientFactory(trust));
		}

		static Material selfSigned(Path dir, String hostname) throws Exception {
			Files.createDirectories(dir);
			Path server = dir.resolve("server.p12");
			keytool(dir, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
					"-storetype", "PKCS12", "-keystore", server.toString(), "-storepass", "changeit", "-keypass",
					"changeit", "-dname", "CN=" + hostname, "-ext", "SAN=dns:" + hostname);
			Path emptyTrust = dir.resolve("trust.p12");
			KeyStore trust = KeyStore.getInstance("PKCS12");
			trust.load(null, PASSWORD);
			try (OutputStream out = Files.newOutputStream(emptyTrust)) {
				trust.store(out, PASSWORD);
			}
			return new Material(server, clientFactory(emptyTrust));
		}

		private static javax.net.ssl.SSLSocketFactory clientFactory(Path trustStore) throws Exception {
			KeyStore trust = KeyStore.getInstance("PKCS12");
			try (InputStream in = Files.newInputStream(trustStore)) {
				trust.load(in, PASSWORD);
			}
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trust);
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tmf.getTrustManagers(), null);
			return context.getSocketFactory();
		}
	}

	private static final class TlsPeer implements AutoCloseable {
		private final SSLServerSocket server;
		private final int status;
		private volatile boolean hang;
		private volatile String requestText = "";
		private volatile String sni = "";
		private final AtomicInteger received = new AtomicInteger();
		private final Thread thread;

		private TlsPeer(SSLServerSocket server, int status) {
			this.server = server;
			this.status = status;
			this.thread = new Thread(this::serve, "payflow-tls-fixture");
			this.thread.setDaemon(true);
			this.thread.start();
		}

		static TlsPeer start(Path keyStorePath, int status) throws Exception {
			KeyStore keys = KeyStore.getInstance("PKCS12");
			try (InputStream in = Files.newInputStream(keyStorePath)) {
				keys.load(in, PASSWORD);
			}
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(keys, PASSWORD);
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), null, null);
			SSLServerSocketFactory factory = context.getServerSocketFactory();
			SSLServerSocket server = (SSLServerSocket) factory.createServerSocket();
			server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1);
			return new TlsPeer(server, status);
		}

		int port() {
			return server.getLocalPort();
		}

		void hangAfterRequest() {
			hang = true;
		}

		int received() {
			return received.get();
		}

		String awaitRequest() throws InterruptedException {
			long deadline = System.currentTimeMillis() + 3000;
			while (requestText.isBlank() && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}
			return requestText;
		}

		String hostHeader() {
			for (String line : requestText.split("\r\n")) {
				if (line.toLowerCase(Locale.ROOT).startsWith("host:")) {
					return line.substring(5).trim();
				}
			}
			return "";
		}

		String sni() {
			return sni;
		}

		String body() {
			int split = requestText.indexOf("\r\n\r\n");
			return split < 0 ? "" : requestText.substring(split + 4);
		}

		private void serve() {
			try (SSLSocket socket = (SSLSocket) server.accept()) {
				socket.setSoTimeout(4000);
				socket.startHandshake();
				if (socket.getSession() instanceof javax.net.ssl.ExtendedSSLSession extended) {
					sni = extended.getRequestedServerNames().stream()
							.filter(name -> name instanceof javax.net.ssl.SNIHostName)
							.map(name -> ((javax.net.ssl.SNIHostName) name).getAsciiName())
							.findFirst()
							.orElse("");
				}
				String http = readHttp(socket.getInputStream());
				requestText = http;
				received.incrementAndGet();
				if (hang) {
					Thread.sleep(10_000);
					return;
				}
				int code = status;
				String reason = code == 302 ? "Found" : "OK";
				String extra = code == 302 ? "Location: https://evil.example/steal\r\n" : "";
				byte[] payload = code == 200 ? "OK".getBytes(StandardCharsets.UTF_8) : new byte[0];
				String response = "HTTP/1.1 " + code + " " + reason + "\r\n"
						+ extra
						+ "Content-Length: " + payload.length + "\r\n"
						+ "Connection: close\r\n\r\n";
				OutputStream out = socket.getOutputStream();
				out.write(response.getBytes(StandardCharsets.US_ASCII));
				out.write(payload);
				out.flush();
			}
			catch (Exception ignored) {
				// Client timeout or handshake rejection closes the socket.
			}
		}

		private static String readHttp(InputStream in) throws Exception {
			ByteArrayOutputStream header = new ByteArrayOutputStream();
			int matched = 0;
			byte[] marker = new byte[] { '\r', '\n', '\r', '\n' };
			while (matched < 4) {
				int value = in.read();
				if (value < 0) {
					break;
				}
				header.write(value);
				matched = value == marker[matched] ? matched + 1 : (value == '\r' ? 1 : 0);
			}
			String head = header.toString(StandardCharsets.ISO_8859_1);
			int length = 0;
			for (String line : head.split("\r\n")) {
				if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
					length = Integer.parseInt(line.substring(15).trim());
				}
			}
			byte[] body = in.readNBytes(length);
			return head + new String(body, StandardCharsets.UTF_8);
		}

		@Override
		public void close() throws Exception {
			server.close();
			thread.join(2000);
		}
	}

	private static void keytool(Path dir, String... args) throws Exception {
		String executable = Path.of(System.getProperty("java.home"), "bin",
				System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "keytool.exe" : "keytool")
				.toString();
		Process process = new ProcessBuilder(prepend(executable, args))
				.redirectErrorStream(true)
				.directory(dir.toFile())
				.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int code = process.waitFor();
		if (code != 0) {
			throw new IllegalStateException("keytool failed (" + code + "): " + output);
		}
	}

	private static String[] prepend(String first, String[] rest) {
		String[] all = new String[rest.length + 1];
		all[0] = first;
		System.arraycopy(rest, 0, all, 1, rest.length);
		return all;
	}
}
