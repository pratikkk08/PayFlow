package com.payflow.risk;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test clock whose instant can be advanced without rebuilding the Spring context.
 */
public final class MutableClock extends Clock {

	private final AtomicReference<Instant> instant;
	private final ZoneId zone;

	public MutableClock(Instant initial) {
		this.instant = new AtomicReference<>(initial);
		this.zone = ZoneOffset.UTC;
	}

	public void setInstant(Instant instant) {
		this.instant.set(instant);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("zone changes not supported in tests");
	}

	@Override
	public Instant instant() {
		return instant.get();
	}
}
