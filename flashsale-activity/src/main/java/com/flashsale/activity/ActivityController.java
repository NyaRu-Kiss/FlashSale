package com.flashsale.activity;

import com.flashsale.common.api.ApiResponse;
import com.flashsale.common.api.PageResponse;
import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.trace.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
final class ActivityController {
    private final ActivityService service;
    private final JwtTokenService tokens;

    ActivityController(ActivityService service, JwtTokenService tokens) { this.service = service; this.tokens = tokens; }

    @GetMapping("/activities")
    ApiResponse<PageResponse<Activity>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(name = "page_size", defaultValue = "20") int size) {
        validatePage(page, size); return ok(page(service.listPublic(), page, size, service.countPublic()));
    }

    @GetMapping("/activities/{id}")
    ApiResponse<Activity> get(@PathVariable long id) { return ok(service.getPublic(id)); }

    @GetMapping("/admin/activities")
    ApiResponse<PageResponse<Activity>> adminList(@RequestHeader("Authorization") String header,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(name = "page_size", defaultValue = "20") int size) {
        validatePage(page, size); Principal actor = actor(header);
        return ok(page(service.listOperator(actor), page, size, service.countOperator(actor)));
    }

    @GetMapping("/admin/activities/{id}")
    ApiResponse<Activity> adminGet(@RequestHeader("Authorization") String header, @PathVariable long id) {
        return ok(service.getOperator(actor(header), id));
    }

    @PostMapping("/admin/activities")
    ApiResponse<Activity> create(@RequestHeader("Authorization") String header, @Valid @RequestBody Request request) {
        Principal actor = actor(header);
        return ok(service.create(actor, request.toActivity()));
    }

    @PostMapping("/admin/activities/{id}/cancel")
    ApiResponse<Activity> cancel(@RequestHeader("Authorization") String header, @PathVariable long id) {
        return ok(service.cancel(actor(header), id));
    }

    @PostMapping("/admin/activities/{id}/start")
    ApiResponse<Activity> start(@RequestHeader("Authorization") String header, @PathVariable long id) {
        return ok(service.start(actor(header), id));
    }

    private Principal actor(String header) {
        try {
            if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException("UNAUTHENTICATED");
            return tokens.parse(header.substring(7));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("UNAUTHENTICATED"); }
    }

    private static void validatePage(int page, int size) { if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("VALIDATION_ERROR"); }
    private static <T> ApiResponse<T> ok(T data) { return ApiResponse.success(data, TraceContext.getOrCreate()); }
    private static <T> PageResponse<T> page(List<T> all, int page, int size, long total) {
        int from = Math.min((page - 1) * size, all.size()); int to = Math.min(from + size, all.size());
        return new PageResponse<>(all.subList(from, to), page, size, total);
    }

    record Request(@NotBlank String name, long productId, @Min(0) long salePriceMinor, @Min(1) int initialStock,
                   @Min(1) int purchaseLimitPerUser, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        Activity toActivity() { return new Activity(0, name, productId, salePriceMinor, initialStock, initialStock,
                purchaseLimitPerUser, startsAt, endsAt, ActivityStatus.NOT_STARTED, false, 0); }
    }
}
