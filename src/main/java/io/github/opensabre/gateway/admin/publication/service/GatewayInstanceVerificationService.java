package io.github.opensabre.gateway.admin.publication.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.opensabre.gateway.admin.integration.GatewayRevisionReadClient;
import io.github.opensabre.gateway.admin.publication.dao.GatewayInstanceRevisionMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceRevision;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceRevisionStatus;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceVerification;
import io.github.opensabre.gateway.admin.service.GatewayServiceCatalogService;
import io.github.opensabre.gateway.admin.service.model.GatewayServiceInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 发布后查询所有健康网关实例，确认本次修订号已经进入实例内存。 */
@Service
public class GatewayInstanceVerificationService {

    private final GatewayServiceCatalogService catalogService;
    private final GatewayRevisionReadClient revisionReadClient;
    private final GatewayInstanceRevisionMapper revisionMapper;
    private final String gatewayServiceName;

    public GatewayInstanceVerificationService(GatewayServiceCatalogService catalogService,
            GatewayRevisionReadClient revisionReadClient,
            GatewayInstanceRevisionMapper revisionMapper,
            @Value("${opensabre.gateway-admin.gateway-service-name:base-gateway}") String gatewayServiceName) {
        this.catalogService = catalogService;
        this.revisionReadClient = revisionReadClient;
        this.revisionMapper = revisionMapper;
        this.gatewayServiceName = gatewayServiceName;
    }

    /** 执行一次即时确认；未加载和不可达均保留为可再次确认的状态。 */
    public GatewayInstanceVerification verify(String releaseId) {
        List<GatewayServiceInstance> instances;
        try {
            instances = catalogService.getService(gatewayServiceName).instances().stream()
                    .filter(instance -> instance.healthy() && instance.enabled()).toList();
        } catch (RuntimeException exception) {
            return new GatewayInstanceVerification(0, 0, List.of());
        }
        List<GatewayInstanceRevision> results = new ArrayList<>();
        int loaded = 0;
        for (GatewayServiceInstance instance : instances) {
            GatewayInstanceRevision result = inspect(releaseId, instance);
            if (result.getStatus() == GatewayInstanceRevisionStatus.LOADED) loaded++;
            if (!persist(result)) {
                throw new IllegalStateException("保存网关实例生效状态失败：" + result.getInstanceId());
            }
            results.add(result);
        }
        return new GatewayInstanceVerification(results.size(), loaded, List.copyOf(results));
    }

    private boolean persist(GatewayInstanceRevision result) {
        GatewayInstanceRevision current = revisionMapper.selectOne(
                new LambdaQueryWrapper<GatewayInstanceRevision>()
                        .eq(GatewayInstanceRevision::getReleaseId, result.getReleaseId())
                        .eq(GatewayInstanceRevision::getInstanceId, result.getInstanceId()));
        if (current == null) return revisionMapper.insert(result) == 1;
        current.setLoadedVersion(result.getLoadedVersion());
        current.setStatus(result.getStatus());
        current.setErrorMessage(result.getErrorMessage());
        current.setReportedTime(result.getReportedTime());
        return revisionMapper.updateById(current) == 1;
    }

    private GatewayInstanceRevision inspect(String expectedRevision, GatewayServiceInstance instance) {
        GatewayInstanceRevision result = new GatewayInstanceRevision();
        result.setReleaseId(expectedRevision);
        result.setInstanceId(instance.ip() + ":" + instance.port());
        result.setReportedTime(new Date());
        try {
            String loadedRevision = revisionReadClient.fetch(instance);
            result.setLoadedVersion(loadedRevision);
            result.setStatus(expectedRevision.equals(loadedRevision)
                    ? GatewayInstanceRevisionStatus.LOADED : GatewayInstanceRevisionStatus.PENDING);
        } catch (RuntimeException exception) {
            result.setStatus(GatewayInstanceRevisionStatus.UNREACHABLE);
            result.setErrorMessage(abbreviate(exception.getMessage()));
        }
        return result;
    }

    private String abbreviate(String value) {
        if (value == null) return "未知实例探测错误";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
