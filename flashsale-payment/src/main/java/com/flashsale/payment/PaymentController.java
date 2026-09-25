package com.flashsale.payment;

import com.flashsale.common.api.ApiResponse;
import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import com.flashsale.common.trace.TraceContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public final class PaymentController {
    private final JdbcPaymentService payments;
    private final JwtTokenService tokens;
    public PaymentController(JdbcPaymentService payments, JwtTokenService tokens) { this.payments = payments; this.tokens = tokens; }

    @PostMapping("/orders/{orderNumber}/payments")
    public ApiResponse<JdbcPaymentService.Result> pay(@RequestHeader("Authorization") String authorization,
                                                       @RequestHeader("Idempotency-Key") String key,
                                                       @PathVariable String orderNumber,
                                                       @Valid @RequestBody PaymentRequest request) {
        Principal actor = customer(authorization);
        if (key == null || key.isBlank()) throw new IllegalArgumentException("VALIDATION_ERROR");
        return ApiResponse.success(payments.pay(actor.userId(), orderNumber, key, request.amountMinor(), request.currency(), request.paymentStatus()), TraceContext.getOrCreate());
    }

    @PostMapping("/payments/callback")
    public ApiResponse<JdbcPaymentService.Result> callback(@Valid @RequestBody CallbackRequest request) {
        return ApiResponse.success(payments.callback(new PaymentGateway.CallbackRequest(request.transactionId(), request.amountMinor(), request.currency(), request.paymentStatus(), request.eventId())), TraceContext.getOrCreate());
    }

    private Principal customer(String authorization) {
        try { if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("UNAUTHENTICATED"); Principal p = tokens.parse(authorization.substring(7)); if (p.role() != Role.CUSTOMER) throw new IllegalArgumentException("FORBIDDEN"); return p; }
        catch (IllegalArgumentException e) { throw e; } catch (Exception e) { throw new IllegalArgumentException("UNAUTHENTICATED"); }
    }
    public record PaymentRequest(@JsonProperty("amount_minor") @Positive long amountMinor, @NotBlank String currency, @JsonProperty("payment_status") String paymentStatus) {}
    public record CallbackRequest(@JsonProperty("provider_transaction_id") @NotBlank String transactionId, @JsonProperty("amount_minor") @Positive long amountMinor, @NotBlank String currency, @JsonProperty("payment_status") @NotBlank String paymentStatus, @JsonProperty("event_id") @NotBlank String eventId) {}
}
