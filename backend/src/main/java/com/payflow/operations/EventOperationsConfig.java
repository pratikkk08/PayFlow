package com.payflow.operations;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventOperationsProperties.class)
public class EventOperationsConfig {
}
