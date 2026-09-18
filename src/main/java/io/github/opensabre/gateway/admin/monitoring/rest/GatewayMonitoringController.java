package io.github.opensabre.gateway.admin.monitoring.rest;

import io.github.opensabre.gateway.admin.monitoring.model.GatewayRouteMetricsSnapshot;
import io.github.opensabre.gateway.admin.monitoring.model.GatewayInstanceRuntime;
import io.github.opensabre.gateway.admin.monitoring.model.ApplicationMetricsSnapshot;
import io.github.opensabre.gateway.admin.monitoring.model.MonitoringDataSourceStatus;
import io.github.opensabre.gateway.admin.monitoring.model.MonitoringHistory;
import io.github.opensabre.monitoring.model.ApplicationInstanceMonitoring;
import io.github.opensabre.gateway.admin.monitoring.service.ApplicationActuatorMonitoringService;
import io.github.opensabre.gateway.admin.monitoring.service.GatewayRuntimeMonitoringService;
import io.github.opensabre.gateway.admin.monitoring.service.MonitoringQueryService;
import static io.github.opensabre.gateway.admin.monitoring.service.ControlPlaneMonitoringQueries.*;
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

    private final MonitoringQueryService monitoringQueries;
    private final GatewayRuntimeMonitoringService runtimeMonitoringService;
    private final ApplicationActuatorMonitoringService actuatorMonitoringService;

    public GatewayMonitoringController(MonitoringQueryService monitoringQueries,
            GatewayRuntimeMonitoringService runtimeMonitoringService,
            ApplicationActuatorMonitoringService actuatorMonitoringService) {
        this.monitoringQueries = monitoringQueries;
        this.runtimeMonitoringService = runtimeMonitoringService;
        this.actuatorMonitoringService = actuatorMonitoringService;
    }

    @GetMapping("/routes")
    @Operation(summary = "查询网关路由请求率、错误率和 P95 延迟")
    public GatewayRouteMetricsSnapshot routes() {
        return new GatewayRouteMetricsSnapshot(
                queryOrEmpty(GATEWAY_REQUEST_RATE),
                queryOrEmpty(GATEWAY_ERROR_RATE),
                queryOrEmpty(GATEWAY_P95_LATENCY));
    }

    /** Query basic traffic and process metrics for all discovered application instances. */
    @GetMapping("/applications")
    @Operation(summary = "查询应用实例请求、CPU 和堆内存指标")
    public ApplicationMetricsSnapshot applications() {
        return new ApplicationMetricsSnapshot(
                queryOrEmpty(APPLICATION_REQUEST_RATE),
                queryOrEmpty(APPLICATION_ERROR_RATE),
                queryOrEmpty(APPLICATION_P95_LATENCY),
                queryOrEmpty(APPLICATION_CPU_USAGE),
                queryOrEmpty(APPLICATION_HEAP_USED),
                queryOrEmpty(APPLICATION_HEAP_MAX));
    }

    @GetMapping("/routes/history")
    @Operation(summary = "按时间范围查询网关路由 TPS 和延迟趋势")
    public MonitoringHistory routeHistory(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1h") String range,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String routeId) {
        return monitoringQueries.routeHistory(range, routeId);
    }

    @GetMapping("/applications/history")
    @Operation(summary = "按时间范围查询应用或实例运行趋势")
    public MonitoringHistory applicationHistory(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1h") String range,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String application,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String instance) {
        return monitoringQueries.applicationHistory(range, application, instance);
    }

    @GetMapping("/status")
    @Operation(summary = "查询 Prometheus 数据源及采集目标状态")
    public MonitoringDataSourceStatus status() {
        return monitoringQueries.status();
    }

    /** Return instantaneous Actuator metrics for the same Nacos page shown by service management. */
    @GetMapping("/actuator")
    @Operation(summary = "查询应用实例 Actuator 基础指标")
    public List<ApplicationInstanceMonitoring> actuator(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int pageSize) {
        return actuatorMonitoringService.snapshots(page, pageSize);
    }

    @GetMapping("/runtime")
    @Operation(summary = "查询各网关实例最终生效的运行参数")
    public List<GatewayInstanceRuntime> runtime() {
        return runtimeMonitoringService.snapshots();
    }

    private String queryOrEmpty(String promql) {
        try {
            return monitoringQueries.query(promql);
        } catch (IllegalStateException unavailable) {
            return EMPTY_VECTOR;
        }
    }
}
