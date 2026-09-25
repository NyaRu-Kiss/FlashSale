package com.flashsale.common.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Map;
import com.flashsale.common.trace.StructuredLogContext;

/** Wraps a business handler with at-least-once idempotency semantics. */
public final class ConsumerMessageHandler {
    private final ConsumerIdempotencyPort idempotency;
    private final Clock clock;
    private final Duration processingTimeout;
    private final BusinessHandler business;

    public ConsumerMessageHandler(ConsumerIdempotencyPort idempotency, Clock clock,
                                  Duration processingTimeout, BusinessHandler business) {
        this.idempotency = Objects.requireNonNull(idempotency);
        this.clock = Objects.requireNonNull(clock);
        if (processingTimeout.isNegative() || processingTimeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        this.processingTimeout = processingTimeout;
        this.business = Objects.requireNonNull(business);
    }

    public HandleResult handle(MessageEnvelope message) {
        Instant now = Instant.now(clock);
        if (!idempotency.tryStart(message.idempotencyKey(), message.eventId().toString(), now, processingTimeout))
            return HandleResult.DUPLICATE;
        try (StructuredLogContext ignored = StructuredLogContext.openMessage(message)) {
            business.process(message);
            idempotency.markSucceeded(message.idempotencyKey(), Instant.now(clock));
            return HandleResult.SUCCEEDED;
        } catch (Exception error) {
            try (StructuredLogContext ignored = StructuredLogContext.open(Map.of(
                    StructuredLogContext.ERROR_CODE, errorCode(error)))) {
            idempotency.markFailed(message.idempotencyKey(), Instant.now(clock), error.toString());
            }
            return HandleResult.RETRY;
        }
    }

    private static String errorCode(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message.split("[\\s:]", 2)[0];
    }

    public enum HandleResult { SUCCEEDED, DUPLICATE, RETRY }
    @FunctionalInterface public interface BusinessHandler { void process(MessageEnvelope message) throws Exception; }
}
