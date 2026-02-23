package com.odin.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita suporte ao @Scheduled para agendamento de relatórios (Story 7.1).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
