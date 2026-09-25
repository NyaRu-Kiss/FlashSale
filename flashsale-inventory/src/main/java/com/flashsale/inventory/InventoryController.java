package com.flashsale.inventory;

import com.flashsale.common.api.ApiResponse;
import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import com.flashsale.common.trace.TraceContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public final class InventoryController {
    private final InventoryLedger ledger;
    private final JwtTokenService tokens;
    public InventoryController(InventoryLedger ledger, JwtTokenService tokens) { this.ledger = ledger; this.tokens = tokens; }
    @PostMapping("/reservations")
    public ApiResponse<InventoryReservation> reserve(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @Valid @RequestBody ReserveRequest request) {
        internalActor(authorization);
        return ApiResponse.success(ledger.reserve(request.key(), request.resourceId(), request.quantity()), TraceContext.getOrCreate());
    }
    @PostMapping("/reservations/{key}/confirm")
    public ApiResponse<InventoryReservation> confirm(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String key) {
        internalActor(authorization);
        return ApiResponse.success(ledger.confirm(key), TraceContext.getOrCreate());
    }
    @PostMapping("/reservations/{key}/release")
    public ApiResponse<InventoryReservation> release(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String key) {
        internalActor(authorization);
        return ApiResponse.success(ledger.release(key), TraceContext.getOrCreate());
    }
    private Principal internalActor(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("UNAUTHENTICATED");
            Principal principal = tokens.parse(authorization.substring(7));
            if (principal.role() == Role.CUSTOMER) throw new IllegalArgumentException("FORBIDDEN");
            return principal;
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("UNAUTHENTICATED"); }
    }
    public record ReserveRequest(String key, @JsonProperty("resource_id") @Positive long resourceId, @Positive int quantity) {}
}
