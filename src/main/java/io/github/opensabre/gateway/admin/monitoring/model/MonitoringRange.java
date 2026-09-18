package io.github.opensabre.gateway.admin.monitoring.model;

import java.time.Duration;

/** Bounded query ranges keep chart responses predictable and protect Prometheus. */
public enum MonitoringRange {
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15), Duration.ofSeconds(15)),
    ONE_HOUR("1h", Duration.ofHours(1), Duration.ofSeconds(30)),
    SIX_HOURS("6h", Duration.ofHours(6), Duration.ofMinutes(2)),
    ONE_DAY("24h", Duration.ofDays(1), Duration.ofMinutes(5)),
    SEVEN_DAYS("7d", Duration.ofDays(7), Duration.ofMinutes(30)),
    THIRTY_DAYS("30d", Duration.ofDays(30), Duration.ofHours(2));

    private final String value;
    private final Duration duration;
    private final Duration step;

    MonitoringRange(String value, Duration duration, Duration step) {
        this.value = value;
        this.duration = duration;
        this.step = step;
    }

    public Duration duration() { return duration; }
    public Duration step() { return step; }
    public String value() { return value; }

    public static MonitoringRange parse(String value) {
        for (MonitoringRange range : values()) {
            if (range.value.equals(value)) return range;
        }
        throw new IllegalArgumentException("Unsupported monitoring range: " + value);
    }
}
