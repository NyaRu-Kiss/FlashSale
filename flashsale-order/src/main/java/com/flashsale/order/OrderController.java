package com.flashsale.order;

import com.flashsale.common.api.ApiResponse;
import com.flashsale.common.api.PageResponse;
import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import com.flashsale.common.trace.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public final class OrderController {
    private final OrderService orders;
    private final OrderPreviewService previews;
    private final OrderCancellationService cancellations;
    private final OrderQueryService queries;
    private final JwtTokenService tokens;

    public OrderController(OrderService orders, OrderPreviewService previews,
                            OrderCancellationService cancellations, OrderQueryService queries,
                            JwtTokenService tokens) {
        this.orders = orders; this.previews = previews; this.cancellations = cancellations;
        this.queries = queries; this.tokens = tokens;
    }

    @PostMapping("/preview")
    public ApiResponse<OrderPreview> preview(@RequestHeader("Authorization") String authorization,
                                              @Valid @RequestBody Request request) {
        Principal actor = customer(authorization);
        return ApiResponse.success(previews.preview(request.toDomain(actor.userId())), TraceContext.getOrCreate());
    }

    @PostMapping
    public ApiResponse<Order> create(@RequestHeader("Authorization") String authorization,
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody Request request) {
        Principal actor = customer(authorization);
        if (idempotencyKey == null || !idempotencyKey.startsWith("ORDER_SUBMIT_") || idempotencyKey.length() > 128)
            throw new IllegalArgumentException("VALIDATION_ERROR");
        return ApiResponse.success(orders.create(new OrderCreateRequest(actor.userId(), idempotencyKey,
                request.toDomain(actor.userId()))), TraceContext.getOrCreate());
    }

    @GetMapping
    public ApiResponse<PageResponse<Order>> list(@RequestHeader("Authorization") String authorization,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        Principal actor = customer(authorization);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("VALIDATION_ERROR");
        return ApiResponse.success(queries.list(actor.userId(), page, pageSize), TraceContext.getOrCreate());
    }

    @GetMapping("/{orderNumber}")
    public ApiResponse<Order> get(@RequestHeader("Authorization") String authorization,
                                   @PathVariable String orderNumber) {
        return ApiResponse.success(queries.get(customer(authorization).userId(), orderNumber), TraceContext.getOrCreate());
    }

    @PostMapping("/{orderNumber}/cancel")
    public ApiResponse<Order> cancel(@RequestHeader("Authorization") String authorization,
                                     @PathVariable String orderNumber, @RequestBody(required = false) CancelRequest request) {
        Principal actor = customer(authorization);
        String reason = request == null || request.reason() == null ? "USER_CANCELLED" : request.reason();
        return ApiResponse.success(cancellations.cancel(actor.userId(), orderNumber, reason), TraceContext.getOrCreate());
    }

    private Principal customer(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("UNAUTHENTICATED");
            Principal principal = tokens.parse(authorization.substring(7));
            if (principal.role() != Role.CUSTOMER) throw new IllegalArgumentException("FORBIDDEN");
            return principal;
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("UNAUTHENTICATED"); }
    }

    public record CancelRequest(String reason) {}
    public record Request(OrderKind kind, @JsonProperty("activity_id") Long activityId,
                          @JsonProperty("user_coupon_id") Long userCouponId,
                          @NotEmpty java.util.List<Item> items) {
        OrderPreviewRequest toDomain(long userId) {
            return new OrderPreviewRequest(userId, kind, activityId, userCouponId,
                    items.stream().map(i -> new OrderPreviewRequest.Item(i.productId(), i.quantity())).toList());
        }
    }
    public record Item(@JsonProperty("product_id") @Positive long productId, @Positive int quantity) {}
}
