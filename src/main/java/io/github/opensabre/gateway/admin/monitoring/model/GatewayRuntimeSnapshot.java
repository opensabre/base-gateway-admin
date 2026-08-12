package io.github.opensabre.gateway.admin.monitoring.model;

import java.util.Map;

/** Safe runtime values returned by a gateway instance. */
public record GatewayRuntimeSnapshot(String revision, String applicationVersion, long uptimeSeconds,
        int availableProcessors, JvmSnapshot jvm, NettySnapshot netty, HttpClientSnapshot httpClient,
        int routeCount, Map<String, String> sources) {
    public record JvmSnapshot(String javaVersion, String vendor, long heapUsedBytes, long heapMaxBytes,
            long nonHeapUsedBytes, int liveThreads, int peakThreads) {}
    public record NettySnapshot(int workerThreads, int selectorThreads) {}
    public record HttpClientSnapshot(String poolType, int maxConnections, long acquireTimeoutMillis,
            long connectTimeoutMillis, Long responseTimeoutMillis, Long maxIdleTimeMillis,
            Long maxLifeTimeMillis) {}
}
