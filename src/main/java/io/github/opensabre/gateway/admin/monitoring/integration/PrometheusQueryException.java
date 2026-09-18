package io.github.opensabre.gateway.admin.monitoring.integration;

/** A typed failure that lets the API distinguish an unavailable data source from empty data. */
public class PrometheusQueryException extends IllegalStateException {
    public PrometheusQueryException(String message) {
        super(message);
    }

    public PrometheusQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
