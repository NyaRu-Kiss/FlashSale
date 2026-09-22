package com.flashsale.common.security;

public record Principal(long userId, Role role) {
    public boolean canManageBusiness() { return role == Role.OPERATOR; }
    public boolean canManageAccounts() { return role == Role.ADMIN; }
}
