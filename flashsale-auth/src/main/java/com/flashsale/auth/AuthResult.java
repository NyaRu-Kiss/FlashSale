package com.flashsale.auth;

public record AuthResult(long userId, String username, String role, String token) {}
