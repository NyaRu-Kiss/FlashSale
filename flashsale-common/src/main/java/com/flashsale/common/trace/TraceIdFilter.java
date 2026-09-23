package com.flashsale.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;

public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.HEADER);
        if (traceId == null || traceId.isBlank()) traceId = TraceContext.getOrCreate();
        TraceContext.set(traceId);
        MDC.put("trace_id", traceId);
        response.setHeader(TraceContext.HEADER, traceId);
        try { filterChain.doFilter(request, response); }
        finally { MDC.remove("trace_id"); TraceContext.clear(); }
    }
}
