package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AccountLifecycleProperties.class, MailOutboxProperties.class, AccountRateLimitProperties.class})
public class AccountLifecycleConfig {}
