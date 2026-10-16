package com.payflow.risk.service;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates JSONB rule parameters for each supported {@link RiskRuleType}.
 *
 * <ul>
 *   <li>{@code AMOUNT_THRESHOLD}: {@code thresholdMinor} (integer ≥ 0)</li>
 *   <li>{@code PAYMENT_VELOCITY}: {@code maxCount} (integer ≥ 0), {@code windowSeconds} (integer &gt; 0)</li>
 *   <li>{@code REPEATED_FAILURES}: {@code maxFailures} (integer ≥ 0), {@code windowSeconds} (integer &gt; 0)</li>
 * </ul>
 */
@Component
public class RiskRuleParameterParser {

	private final JsonMapper jsonMapper;

	public RiskRuleParameterParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public AmountThresholdParams parseAmountThreshold(String rawJson) {
		JsonNode root = requireObject(rawJson);
		long thresholdMinor = requireNonNegativeLong(root, "thresholdMinor");
		return new AmountThresholdParams(thresholdMinor);
	}

	public VelocityParams parseVelocity(String rawJson) {
		JsonNode root = requireObject(rawJson);
		int maxCount = requireNonNegativeInt(root, "maxCount");
		int windowSeconds = requirePositiveInt(root, "windowSeconds");
		return new VelocityParams(maxCount, windowSeconds);
	}

	public RepeatedFailuresParams parseRepeatedFailures(String rawJson) {
		JsonNode root = requireObject(rawJson);
		int maxFailures = requireNonNegativeInt(root, "maxFailures");
		int windowSeconds = requirePositiveInt(root, "windowSeconds");
		return new RepeatedFailuresParams(maxFailures, windowSeconds);
	}

	public record AmountThresholdParams(long thresholdMinor) {
	}

	public record VelocityParams(int maxCount, int windowSeconds) {
	}

	public record RepeatedFailuresParams(int maxFailures, int windowSeconds) {
	}

	public static final class InvalidRiskRuleParametersException extends RuntimeException {
		public InvalidRiskRuleParametersException(String message) {
			super(message);
		}

		public InvalidRiskRuleParametersException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private JsonNode requireObject(String rawJson) {
		if (rawJson == null || rawJson.isBlank()) {
			throw new InvalidRiskRuleParametersException("empty_parameters");
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(rawJson);
		}
		catch (RuntimeException ex) {
			throw new InvalidRiskRuleParametersException("malformed_json", ex);
		}
		if (root == null || !root.isObject()) {
			throw new InvalidRiskRuleParametersException("parameters_not_object");
		}
		return root;
	}

	private static long requireNonNegativeLong(JsonNode root, String field) {
		JsonNode node = root.get(field);
		if (node == null || node.isNull() || !node.isNumber()) {
			throw new InvalidRiskRuleParametersException("missing_or_invalid:" + field);
		}
		long value = node.asLong();
		if (node.asDouble() != value) {
			throw new InvalidRiskRuleParametersException("non_integer:" + field);
		}
		if (value < 0) {
			throw new InvalidRiskRuleParametersException("negative:" + field);
		}
		return value;
	}

	private static int requireNonNegativeInt(JsonNode root, String field) {
		long value = requireNonNegativeLong(root, field);
		if (value > Integer.MAX_VALUE) {
			throw new InvalidRiskRuleParametersException("out_of_range:" + field);
		}
		return (int) value;
	}

	private static int requirePositiveInt(JsonNode root, String field) {
		int value = requireNonNegativeInt(root, field);
		if (value <= 0) {
			throw new InvalidRiskRuleParametersException("non_positive:" + field);
		}
		return value;
	}
}
