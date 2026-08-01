package com.osc.monitor.simulator;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.simulator")
public record SimulatorProperties(@Positive int mutations) {
}
