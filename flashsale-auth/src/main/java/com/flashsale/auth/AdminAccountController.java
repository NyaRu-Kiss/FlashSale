package com.flashsale.auth;

import com.flashsale.common.api.ApiResponse;
import com.flashsale.common.api.PageResponse;
import com.flashsale.common.security.JwtTokenService;
import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import com.flashsale.common.trace.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminAccountController {
    private final AdminAccountService service; private final JwtTokenService tokens;
    AdminAccountController(AdminAccountService service, JwtTokenService tokens){this.service=service;this.tokens=tokens;}
    @PostMapping public ApiResponse<AuthUserView> create(@RequestHeader("Authorization") String auth,@Valid @RequestBody CreateRequest r){ AuthUser u=service.create(actor(auth),r.username(),r.password(),r.role()); return ok(view(u)); }
    @GetMapping public ApiResponse<PageResponse<AuthUserView>> list(@RequestHeader("Authorization") String auth,@RequestParam(defaultValue="1") int page,@RequestParam(name="page_size",defaultValue="20") int size){ var items=service.list(actor(auth),page,size).stream().map(this::view).toList(); return ApiResponse.success(new PageResponse<>(items,page,size,service.count(actor(auth))),TraceContext.getOrCreate()); }
    @GetMapping("/{id}") public ApiResponse<AuthUserView> get(@RequestHeader("Authorization") String auth,@PathVariable long id){ return ok(view(service.get(actor(auth),id))); }
    @PatchMapping("/{id}/role") public ApiResponse<AuthUserView> role(@RequestHeader("Authorization") String auth,@PathVariable long id,@RequestBody RoleRequest r){ return ok(view(service.changeRole(actor(auth),id,r.role()))); }
    @PostMapping("/{id}/enable") public ApiResponse<AuthUserView> enable(@RequestHeader("Authorization") String auth,@PathVariable long id){ return ok(view(service.setStatus(actor(auth),id,"ACTIVE"))); }
    @PostMapping("/{id}/disable") public ApiResponse<AuthUserView> disable(@RequestHeader("Authorization") String auth,@PathVariable long id){ return ok(view(service.setStatus(actor(auth),id,"DISABLED"))); }
    private ApiResponse<AuthUserView> ok(AuthUserView v){ return ApiResponse.success(v,TraceContext.getOrCreate()); }
    private Principal actor(String h){ if(h==null||!h.startsWith("Bearer ")) throw new IllegalArgumentException("UNAUTHENTICATED"); try{return tokens.parse(h.substring(7));}catch(Exception e){throw new IllegalArgumentException("UNAUTHENTICATED");} }
    private AuthUserView view(AuthUser u){ return new AuthUserView(u.id(),u.username(),u.role().name(),u.status()); }
    record CreateRequest(@NotBlank @Size(max=64) String username,@NotBlank @Size(min=8,max=128) String password,Role role){}
    record RoleRequest(Role role){}
    record AuthUserView(long id,String username,String role,String status){}
}
