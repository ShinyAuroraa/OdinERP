package com.odin.wms.config.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WmsMetrics}.
 *
 * <p>Uses {@link SimpleMeterRegistry} — no Spring context required.
 * Verifies that all four metric instruments are registered on startup
 * and that the stock movements counter increments correctly.
 */
class WmsMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WmsMetrics metrics = new WmsMetrics(registry);

    // ── Metric registration ──────────────────────────────────────────────────

    @Test
    void stockMovementsCounterIsRegistered() {
        Counter counter = registry.find(WmsMetrics.STOCK_MOVEMENTS).counter();
        assertThat(counter).isNotNull();
    }

    @Test
    void operationErrorsCounterIsRegistered() {
        Counter counter = registry.find(WmsMetrics.OPERATION_ERRORS).counter();
        assertThat(counter).isNotNull();
    }

    @Test
    void apiRequestDurationTimerIsRegistered() {
        Timer timer = registry.find(WmsMetrics.API_REQUEST_DURATION).timer();
        assertThat(timer).isNotNull();
    }

    @Test
    void activeTenantsGaugeIsRegistered() {
        Gauge gauge = registry.find(WmsMetrics.ACTIVE_TENANTS_GAUGE).gauge();
        assertThat(gauge).isNotNull();
    }

    // ── Metric semantics ─────────────────────────────────────────────────────

    @Test
    void incrementStockMovementsUpdatesCounter() {
        Counter counter = registry.find(WmsMetrics.STOCK_MOVEMENTS).counter();
        assertThat(counter).isNotNull();

        double before = counter.count();
        metrics.incrementStockMovements();

        assertThat(counter.count()).isEqualTo(before + 1.0);
    }
}
