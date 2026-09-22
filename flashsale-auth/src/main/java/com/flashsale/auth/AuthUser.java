package com.flashsale.auth;

import com.flashsale.common.security.Role;

public record AuthUser(long id, String username, String passwordHash, Role role, String status) {
    public boolean active() { return "ACTIVE".equals(status); }
}
