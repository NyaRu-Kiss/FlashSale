package com.flashsale.gateway;

import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import com.flashsale.common.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewaySecurityFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    public GatewaySecurityFilter(JwtTokenService tokens){this.tokens=tokens;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String trace=TraceContext.getOrCreate(); res.setHeader("X-Trace-Id",trace);
        if (!allowRate(req.getRemoteAddr())) { write(res,429,"RATE_LIMITED","rate limit exceeded",trace); return; }
        String path=req.getRequestURI();
        if (isPublic(req.getMethod(), path)) { chain.doFilter(req,res); return; }
        String auth=req.getHeader("Authorization"); Principal principal;
        try { if(auth==null||!auth.startsWith("Bearer ")) throw new IllegalArgumentException(); principal=tokens.parse(auth.substring(7)); }
        catch(Exception e){ write(res,401,"UNAUTHENTICATED","authentication required",trace); return; }
        if (path.startsWith("/api/v1/admin/users") && principal.role()!=Role.ADMIN) { write(res,403,"FORBIDDEN","admin role required",trace); return; }
        if (isBusinessAdminPath(path) && principal.role()!=Role.OPERATOR) { write(res,403,"FORBIDDEN","operator role required",trace); return; }
        if (!path.startsWith("/api/v1/admin/") && principal.role()!=Role.CUSTOMER) { write(res,403,"FORBIDDEN","customer role required",trace); return; }
        req.setAttribute("principal",principal); chain.doFilter(req,res);
    }
    private boolean isPublic(String method, String p){ return p.equals("/actuator/health")||p.startsWith("/api/v1/auth/")||("GET".equals(method)&&(p.startsWith("/api/v1/products")||p.startsWith("/api/v1/activities")))||("POST".equals(method)&&p.equals("/api/v1/payments/callback")); }
    private boolean isBusinessAdminPath(String p){ return p.startsWith("/api/v1/admin/products")||p.startsWith("/api/v1/admin/activities")||p.startsWith("/api/v1/admin/coupon-templates"); }
    private boolean allowRate(String key){ long now=System.currentTimeMillis()/1000; Window w=windows.computeIfAbsent(key,k->new Window(now)); if(w.second!=now){w.second=now;w.count.set(0);} return w.count.incrementAndGet()<=100; }
    private void write(HttpServletResponse r,int status,String code,String message,String trace)throws IOException{r.setStatus(status);r.setContentType("application/json");r.getWriter().write(String.format("{\"code\":\"%s\",\"message\":\"%s\",\"trace_id\":\"%s\"}",code,message,trace));}
    private static final class Window { volatile long second; final AtomicLong count=new AtomicLong(); Window(long second){this.second=second;} }
}
