package com.ifsc.contacerta.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.initial-admin")
public record InitialAdminProperties(String name, String email, String password) {
}
