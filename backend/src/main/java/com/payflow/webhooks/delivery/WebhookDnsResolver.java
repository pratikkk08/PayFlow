package com.payflow.webhooks.delivery;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
public interface WebhookDnsResolver {

	InetAddress[] resolve(String hostname) throws UnknownHostException;
}
