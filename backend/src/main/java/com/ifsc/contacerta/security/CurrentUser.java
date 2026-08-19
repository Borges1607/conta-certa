package com.ifsc.contacerta.security;

import com.ifsc.contacerta.model.Role;

import java.util.UUID;

public record CurrentUser(UUID userId, Role role, UUID sessionId) {
}
