package com.odin.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita execução assíncrona com @Async.
 * Necessário para TraceabilityIndexer.indexMovementAsync().
 */
@Configuration
@EnableAsync
public class AsyncConfig {}
