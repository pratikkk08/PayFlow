package com.payflow.webhooks.delivery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Forces SNI / peer host to the original webhook hostname when the TCP peer is an IP.
 */
final class SniSslSocketFactory extends SSLSocketFactory {

	private final SSLSocketFactory delegate;
	private final String hostname;

	SniSslSocketFactory(SSLSocketFactory delegate, String hostname) {
		this.delegate = delegate;
		this.hostname = hostname;
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return delegate.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return delegate.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
		SSLSocket socket = (SSLSocket) delegate.createSocket(s, hostname, port, autoClose);
		applySni(socket);
		return socket;
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException {
		SSLSocket socket = (SSLSocket) delegate.createSocket(host, port);
		applySni(socket);
		return socket;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
		SSLSocket socket = (SSLSocket) delegate.createSocket(host, port, localHost, localPort);
		applySni(socket);
		return socket;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		SSLSocket socket = (SSLSocket) delegate.createSocket(host, port);
		applySni(socket);
		return socket;
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
			throws IOException {
		SSLSocket socket = (SSLSocket) delegate.createSocket(address, port, localAddress, localPort);
		applySni(socket);
		return socket;
	}

	private void applySni(SSLSocket socket) {
		SSLParameters params = socket.getSSLParameters();
		params.setServerNames(List.of(new SNIHostName(hostname)));
		params.setEndpointIdentificationAlgorithm("HTTPS");
		socket.setSSLParameters(params);
	}
}
