package com.odin.wms.config.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry for WMS-specific Micrometer metrics.
 *
 * <p>Pre-registers four custom instruments at application startup so they appear
 * in the {@code /actuator/prometheus} endpoint with correct metadata even before
 * the first event is recorded:
 *
 * <ul>
 *   <li>{@code wms.stock.movements}   — Counter: total stock movements processed</li>
 *   <li>{@code wms.operations.errors} — Counter: total operation errors, tagged by {@code operation}</li>
 *   <li>{@code wms.api.request.duration} — Timer: API request latency</li>
 *   <li>{@code wms.active.tenants}    — Gauge: tenants with active requests (via {@link AtomicInteger})</li>
 * </ul>
 *
 * <p>Wave 2+ services inject this bean to record business events.
 * Metric names follow the {@code wms.<domain>.<operation>} convention;
 * Prometheus exports them as {@code wms_<domain>_<operation>_total} (Counter/Timer)
 * or {@code wms_<domain>_<operation>} (Gauge).
 */
@Component
public class WmsMetrics {

    public static final String STOCK_MOVEMENTS      = "wms.stock.movements";
    public static final String OPERATION_ERRORS     = "wms.operations.errors";
    public static final String API_REQUEST_DURATION = "wms.api.request.duration";
    public static final String ACTIVE_TENANTS_GAUGE = "wms.active.tenants";

    private final Counter stockMovementsCounter;
    private final AtomicInteger activeTenantsCount;

    public WmsMetrics(MeterRegistry registry) {
        this.stockMovementsCounter = Counter.builder(STOCK_MOVEMENTS)
                .description("Total de movimentações de estoque processadas")
                .tag("service", "wms")
                .register(registry);

        // Timer and error counter are pre-registered with base tags so they appear
        // in /actuator/prometheus from startup; Wave 2+ services will also create
        // tagged variants (per endpoint/operation) on the fly.
        Counter.builder(OPERATION_ERRORS)
                .description("Total de erros em operações WMS")
                .tag("operation", "unknown")
                .register(registry);

        Timer.builder(API_REQUEST_DURATION)
                .description("Duração das requisições às APIs WMS")
                .tag("endpoint", "unknown")
                .tag("method", "unknown")
                .tag("status", "unknown")
                .register(registry);

        this.activeTenantsCount = registry.gauge(
                ACTIVE_TENANTS_GAUGE,
                Tags.of("service", "wms"),
                new AtomicInteger(0));
    }

    /** Increments the stock movements counter by one. */
    public void incrementStockMovements() {
        stockMovementsCounter.increment();
    }

    /** Increments the stock movements counter by the given {@code amount}. */
    public void incrementStockMovements(double amount) {
        stockMovementsCounter.increment(amount);
    }

    /**
     * Returns the mutable {@link AtomicInteger} backing the active-tenants gauge.
     * Callers may call {@code .set(n)} to update the gauge value.
     */
    public AtomicInteger getActiveTenantsGauge() {
        return activeTenantsCount;
    }
}
