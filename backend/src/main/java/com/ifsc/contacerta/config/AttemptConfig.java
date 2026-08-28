package com.ifsc.contacerta.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(AttemptProperties.class)
@EnableScheduling
public class AttemptConfig {}
