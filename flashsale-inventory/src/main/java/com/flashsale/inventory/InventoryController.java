package com.flashsale.inventory;

import com.flashsale.common.api.ApiResponse;
import com.flashsale.common.trace.TraceContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public final class InventoryController {
    private final InventoryLedger ledger;
    public InventoryController(InventoryLedger ledger) { this.ledger = ledger; }
    @PostMapping("/reservations")
    public ApiResponse<InventoryReservation> reserve(@Valid @RequestBody ReserveRequest request) {
        return ApiResponse.success(ledger.reserve(request.key(), request.resourceId(), request.quantity()), TraceContext.getOrCreate());
    }
    @PostMapping("/reservations/{key}/confirm")
    public ApiResponse<InventoryReservation> confirm(@PathVariable String key) { return ApiResponse.success(ledger.confirm(key), TraceContext.getOrCreate()); }
    @PostMapping("/reservations/{key}/release")
    public ApiResponse<InventoryReservation> release(@PathVariable String key) { return ApiResponse.success(ledger.release(key), TraceContext.getOrCreate()); }
    public record ReserveRequest(String key, @JsonProperty("resource_id") @Positive long resourceId, @Positive int quantity) {}
}
