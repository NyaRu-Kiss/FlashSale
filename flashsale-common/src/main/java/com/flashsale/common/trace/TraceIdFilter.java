package com.flashsale.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import java.util.UUID;

import java.io.IOException;

public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.HEADER);
        if (traceId == null || traceId.isBlank()) traceId = TraceContext.getOrCreate();
        TraceContext.set(traceId);
        MDC.put("trace_id", traceId);
        MDC.put("span_id", UUID.randomUUID().toString());
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) MDC.put("user_id", userId);
        response.setHeader(TraceContext.HEADER, traceId);
        try { filterChain.doFilter(request, response); }
        finally { MDC.clear(); TraceContext.clear(); }
    }
}
