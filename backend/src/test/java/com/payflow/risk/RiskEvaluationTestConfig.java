package com.payflow.risk;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class RiskEvaluationTestConfig {

	public static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock(FIXED_NOW);
	}
}
