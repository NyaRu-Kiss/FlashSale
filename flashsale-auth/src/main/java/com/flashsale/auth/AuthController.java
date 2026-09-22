package com.flashsale.auth;

import com.flashsale.common.api.ApiResponse;
import com.flashsale.common.trace.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/register")
    public ApiResponse<AuthResult> register(@Valid @RequestBody Credentials request) { return ApiResponse.success(service.register(request.username(), request.password()), TraceContext.getOrCreate()); }
    @PostMapping("/login")
    public ApiResponse<AuthResult> login(@Valid @RequestBody Credentials request) { return ApiResponse.success(service.login(request.username(), request.password()), TraceContext.getOrCreate()); }
    public record Credentials(@NotBlank @Size(max = 64) String username, @NotBlank @Size(min = 8, max = 128) String password) {}
}
