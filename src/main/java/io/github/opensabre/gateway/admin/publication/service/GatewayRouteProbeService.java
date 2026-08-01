package io.github.opensabre.gateway.admin.publication.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.gateway.admin.integration.GatewayRouteProbeClient;
import io.github.opensabre.gateway.admin.publication.dao.GatewayRouteProbeMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbe;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbeStatus;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbeSummary;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 在所有网关实例上检查本次托管 Route ID 是否成功进入运行时路由表。 */
@Service
public class GatewayRouteProbeService {

    private final GatewayServiceCatalogService catalogService;
    private final GatewayRouteProbeClient probeClient;
    private final GatewayRouteProbeMapper probeMapper;
    private final ObjectMapper objectMapper;
    private final String gatewayServiceName;

    public GatewayRouteProbeService(GatewayServiceCatalogService catalogService,
            GatewayRouteProbeClient probeClient, GatewayRouteProbeMapper probeMapper,
            ObjectMapper objectMapper,
            @Value("${opensabre.gateway-admin.gateway-service-name:base-gateway}") String gatewayServiceName) {
        this.catalogService = catalogService;
        this.probeClient = probeClient;
        this.probeMapper = probeMapper;
        this.objectMapper = objectMapper;
        this.gatewayServiceName = gatewayServiceName;
    }

    /** 只检查网关内存路由表，不向任何下游 API 发送业务请求。 */
    public GatewayRouteProbeSummary probe(String releaseId, List<String> routeIds) {
        List<GatewayServiceInstance> instances;
        try {
            instances = catalogService.getService(gatewayServiceName).instances().stream()
                    .filter(instance -> instance.healthy() && instance.enabled()).toList();
        } catch (RuntimeException exception) {
            return new GatewayRouteProbeSummary(0, 0, List.of());
        }
        List<GatewayRouteProbe> results = new ArrayList<>();
        int passed = 0;
        for (GatewayServiceInstance instance : instances) {
            GatewayRouteProbe result = inspect(releaseId, routeIds, instance);
            if (result.getStatus() == GatewayRouteProbeStatus.PASSED) passed++;
            if (!persist(result)) {
                throw new IllegalStateException("保存网关路由探测结果失败：" + result.getInstanceId());
            }
            results.add(result);
        }
        return new GatewayRouteProbeSummary(results.size(), passed, List.copyOf(results));
    }

    private GatewayRouteProbe inspect(String revision, List<String> routeIds, GatewayServiceInstance instance) {
        GatewayRouteProbe result = new GatewayRouteProbe();
        result.setReleaseId(revision);
        result.setInstanceId(instance.ip() + ":" + instance.port());
        result.setProbedTime(new Date());
        try {
            List<String> missing = probeClient.probe(instance, revision, routeIds);
            result.setMissingRouteIdsJson(toJson(missing));
            result.setStatus(missing.isEmpty() ? GatewayRouteProbeStatus.PASSED : GatewayRouteProbeStatus.MISSING);
        } catch (RuntimeException exception) {
            result.setStatus(GatewayRouteProbeStatus.UNREACHABLE);
            result.setErrorMessage(abbreviate(exception.getMessage()));
        }
        return result;
    }

    private boolean persist(GatewayRouteProbe result) {
        GatewayRouteProbe current = probeMapper.selectOne(new LambdaQueryWrapper<GatewayRouteProbe>()
                .eq(GatewayRouteProbe::getReleaseId, result.getReleaseId())
                .eq(GatewayRouteProbe::getInstanceId, result.getInstanceId()));
        if (current == null) return probeMapper.insert(result) == 1;
        current.setStatus(result.getStatus());
        current.setMissingRouteIdsJson(result.getMissingRouteIdsJson());
        current.setErrorMessage(result.getErrorMessage());
        current.setProbedTime(result.getProbedTime());
        return probeMapper.updateById(current) == 1;
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化缺失路由失败", exception);
        }
    }

    private String abbreviate(String value) {
        if (value == null) return "未知路由探测错误";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
