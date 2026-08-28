package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttemptProperties.class)
public class AttemptConfig {}
