package com.flashsale.gateway;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import com.flashsale.common.trace.TraceContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** First boundary for trace, Sentinel admission and JWT role isolation. */
@Component
final class GatewayAccessFilter implements GlobalFilter, Ordered {
    private final JwtTokenService tokens;

    GatewayAccessFilter(JwtTokenService tokens) { this.tokens = tokens; }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceContext.HEADER);
        if (traceId == null || traceId.isBlank()) traceId = TraceContext.getOrCreate();
        TraceContext.set(traceId);
        exchange.getResponse().getHeaders().set(TraceContext.HEADER, traceId);

        List<Entry> entries = new ArrayList<>();
        try {
            admit(entries, exchange);
            Principal principal = authenticate(exchange);
            if (principal != null) exchange.getAttributes().put("principal", principal);
            return chain.filter(exchange).doFinally(signal -> {
                exit(entries);
                TraceContext.clear();
            });
        } catch (BlockException blocked) {
            exit(entries);
            TraceContext.clear();
            return rejected(exchange, "RATE_LIMITED", "rate limit exceeded");
        } catch (IllegalArgumentException unauthorized) {
            exit(entries);
            TraceContext.clear();
            String code = unauthorized.getMessage() == null ? "UNAUTHENTICATED" : unauthorized.getMessage();
            return rejected(exchange, code, code.equals("FORBIDDEN") ? "forbidden" : "authentication required");
        }
    }

    private void admit(List<Entry> entries, ServerWebExchange exchange) throws BlockException {
        String rawPath = exchange.getRequest().getPath().value();
        String path = normalizedPath(rawPath);
        entries.add(SphU.entry("gateway:all", EntryType.IN));
        entries.add(SphU.entry("gateway:path:" + path, EntryType.IN));
        String ip = exchange.getRequest().getRemoteAddress() == null
                ? "unknown" : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        entries.add(SphU.entry("gateway:ip", EntryType.IN, 1, ip));
        String activityId = pathParameter(rawPath, "/api/v1/activities/([^/]+)/orders");
        if (activityId != null) {
            entries.add(SphU.entry("gateway:hotspot:activity", EntryType.IN, 1, activityId));
        }
        String templateId = pathParameter(rawPath, "/api/v1/coupons/([^/]+)/claims");
        if (templateId != null) {
            entries.add(SphU.entry("gateway:hotspot:coupon", EntryType.IN, 1, templateId));
        }
    }

    private Principal authenticate(ServerWebExchange exchange) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        if (isPublic(method, path)) return null;
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("UNAUTHENTICATED");
        Principal principal;
        try {
            principal = tokens.parse(authorization.substring(7));
        } catch (Exception error) {
            throw new IllegalArgumentException("UNAUTHENTICATED", error);
        }
        if (path.startsWith("/api/v1/admin/users") && principal.role() != Role.ADMIN) throw new IllegalArgumentException("FORBIDDEN");
        if (isBusinessAdminPath(path) && principal.role() != Role.OPERATOR) throw new IllegalArgumentException("FORBIDDEN");
        if (!path.startsWith("/api/v1/admin/") && principal.role() != Role.CUSTOMER) throw new IllegalArgumentException("FORBIDDEN");
        return principal;
    }

    private static boolean isPublic(String method, String path) {
        return path.equals("/actuator/health") || path.startsWith("/api/v1/auth/")
                || ("GET".equals(method) && (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/activities")))
                || ("POST".equals(method) && path.equals("/api/v1/payments/callback"));
    }

    private static boolean isBusinessAdminPath(String path) {
        return path.startsWith("/api/v1/admin/products") || path.startsWith("/api/v1/admin/activities")
                || path.startsWith("/api/v1/admin/coupon-templates");
    }

    static String normalizedPath(String path) {
        if (path.matches("/api/v1/coupons/[^/]+/claims")) return "/api/v1/coupons/{templateId}/claims";
        if (path.matches("/api/v1/activities/[^/]+/orders")) return "/api/v1/activities/{id}/orders";
        return path;
    }

    static String pathParameter(String path, String expression) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(expression).matcher(path);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static void exit(List<Entry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) entries.get(i).exit();
        entries.clear();
    }

    private static Mono<Void> rejected(ServerWebExchange exchange, String code, String message) {
        HttpStatus status = switch (code) {
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.FORBIDDEN;
        };
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceContext.HEADER);
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"data\":null,\"traceId\":\"" + traceId + "\"}";
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
