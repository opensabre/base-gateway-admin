package io.github.opensabre.gateway.admin.monitoring.rest;

import io.github.opensabre.gateway.admin.integration.PrometheusReadClient;
import io.github.opensabre.gateway.admin.monitoring.model.GatewayRouteMetricsSnapshot;
import io.github.opensabre.gateway.admin.monitoring.model.GatewayInstanceRuntime;
import io.github.opensabre.gateway.admin.monitoring.service.GatewayRuntimeMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 只暴露固定的网关监控查询，禁止客户端提交任意 PromQL。 */
@Tag(name = "网关运行监控")
@RestController
@RequestMapping("/monitoring")
public class GatewayMonitoringController {

    private static final String EMPTY_VECTOR =
            "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";

    private static final String REQUEST_RATE =
            "sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count[5m]))";
    private static final String ERROR_RATE =
            "sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count{status=~\"5..\"}[5m]))";
    private static final String P95_LATENCY =
            "histogram_quantile(0.95, sum by (routeId, le) "
                    + "(rate(spring_cloud_gateway_requests_seconds_bucket[5m])))";

    private final PrometheusReadClient prometheus;
    private final GatewayRuntimeMonitoringService runtimeMonitoringService;

    public GatewayMonitoringController(PrometheusReadClient prometheus,
            GatewayRuntimeMonitoringService runtimeMonitoringService) {
        this.prometheus = prometheus;
        this.runtimeMonitoringService = runtimeMonitoringService;
    }

    @GetMapping("/routes")
    @Operation(summary = "查询网关路由请求率、错误率和 P95 延迟")
    public GatewayRouteMetricsSnapshot routes() {
        return new GatewayRouteMetricsSnapshot(
                queryOrEmpty(REQUEST_RATE),
                queryOrEmpty(ERROR_RATE),
                queryOrEmpty(P95_LATENCY));
    }

    @GetMapping("/runtime")
    @Operation(summary = "查询各网关实例最终生效的运行参数")
    public List<GatewayInstanceRuntime> runtime() {
        return runtimeMonitoringService.snapshots();
    }

    private String queryOrEmpty(String promql) {
        try {
            return prometheus.query(promql);
        } catch (IllegalStateException unavailable) {
            return EMPTY_VECTOR;
        }
    }
}
