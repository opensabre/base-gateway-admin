package io.github.opensabre.gateway.admin.publication.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.opensabre.gateway.admin.api.dao.GatewayApiMapper;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import io.github.opensabre.gateway.admin.policy.model.EffectivePolicy;
import io.github.opensabre.gateway.admin.policy.model.PolicyType;
import io.github.opensabre.gateway.admin.policy.service.GatewayPolicyCompiler;
import io.github.opensabre.gateway.admin.policy.service.GatewayGlobalRuleCompiler;
import io.github.opensabre.gateway.admin.policy.service.GatewayPolicyService;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApiPublicationMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApplicationRouteMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import io.github.opensabre.gateway.admin.publication.model.PublicationStatus;
import io.github.opensabre.gateway.admin.publication.model.ReleaseValidationResult;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 组合草稿、三级策略与当前 Nacos 基线，生成只读发布候选。 */
@Service
public class GatewayReleaseValidationService {

    private final GatewayApiMapper apiMapper;
    private final GatewayApiPublicationMapper publicationMapper;
    private final GatewayApplicationRouteMapper applicationRouteMapper;
    private final GatewayRouteCompiler routeCompiler;
    private final GatewayPolicyService policyService;
    private final GatewayPolicyCompiler policyCompiler;
    private final GatewayGlobalRuleCompiler globalRuleCompiler;
    private final IGatewayRouteConfigService routeConfigService;
    private final GatewayResourceBindingValidator resourceBindingValidator;

    public GatewayReleaseValidationService(GatewayApiMapper apiMapper,
            GatewayApiPublicationMapper publicationMapper,
            GatewayApplicationRouteMapper applicationRouteMapper,
            GatewayRouteCompiler routeCompiler,
            GatewayPolicyService policyService,
            GatewayPolicyCompiler policyCompiler,
            GatewayGlobalRuleCompiler globalRuleCompiler,
            IGatewayRouteConfigService routeConfigService,
            GatewayResourceBindingValidator resourceBindingValidator) {
        this.apiMapper = apiMapper;
        this.publicationMapper = publicationMapper;
        this.applicationRouteMapper = applicationRouteMapper;
        this.routeCompiler = routeCompiler;
        this.policyService = policyService;
        this.policyCompiler = policyCompiler;
        this.globalRuleCompiler = globalRuleCompiler;
        this.routeConfigService = routeConfigService;
        this.resourceBindingValidator = resourceBindingValidator;
    }

    /** 执行完整只读预检，不写数据库和 Nacos。 */
    public ReleaseValidationResult validate(String baseVersion) {
        String currentVersion = routeConfigService.getCurrentConfig().getVersion();
        if (!currentVersion.equals(baseVersion)) {
            throw new IllegalStateException("网关配置已被其他人修改，请刷新后重新预检");
        }
        List<GatewayApiPublication> publications = publicationMapper.selectList(
                new LambdaQueryWrapper<GatewayApiPublication>()
                        .in(GatewayApiPublication::getStatus,
                                PublicationStatus.DRAFT, PublicationStatus.PUBLISHED));
        List<GatewayRouteCompiler.ApiPublicationCandidate> apiCandidates = new ArrayList<>();
        for (GatewayApiPublication publication : publications) {
            GatewayApi api = apiMapper.selectById(publication.getApiId());
            if (api == null) {
                throw new IllegalStateException("发布草稿关联的 API 资产不存在：" + publication.getApiId());
            }
            resourceBindingValidator.validate(publication.getAuthMode(), publication.getResourceId(),
                    api.getHttpMethod(), publication.getExternalPath());
            apiCandidates.add(new GatewayRouteCompiler.ApiPublicationCandidate(api, publication));
        }
        List<GatewayApplicationRoute> applications = applicationRouteMapper.selectList(
                new LambdaQueryWrapper<GatewayApplicationRoute>()
                        .in(GatewayApplicationRoute::getStatus,
                                PublicationStatus.DRAFT, PublicationStatus.PUBLISHED));
        List<GatewayRoute> routes = routeCompiler.compile(apiCandidates, applications);
        Map<String, Map<String, Object>> circuitBreakers = new LinkedHashMap<>();
        for (int index = 0; index < apiCandidates.size(); index++) {
            GatewayApi api = apiCandidates.get(index).api();
            applyPolicies(routes.get(index), api.getServiceId(), api.getId(), circuitBreakers);
        }
        for (int index = 0; index < applications.size(); index++) {
            GatewayApplicationRoute application = applications.get(index);
            applyPolicies(routes.get(apiCandidates.size() + index), application.getServiceId(), null, circuitBreakers);
        }
        return new ReleaseValidationResult(currentVersion, apiCandidates.size(), applications.size(),
                routes, Map.copyOf(circuitBreakers), globalRuleCompiler.compile(
                        policyService.resolve(PolicyType.DEFAULT_FILTERS, null, null),
                        policyService.resolve(PolicyType.SECURITY_HEADERS, null, null),
                        policyService.resolve(PolicyType.CORS, null, null)));
    }

    private void applyPolicies(GatewayRoute route, String serviceId, String apiId,
            Map<String, Map<String, Object>> circuitBreakers) {
        List<EffectivePolicy> policies = List.of(
                policyService.resolve(PolicyType.RATE_LIMIT, serviceId, apiId),
                policyService.resolve(PolicyType.TIMEOUT, serviceId, apiId),
                policyService.resolve(PolicyType.CIRCUIT_BREAKER, serviceId, apiId),
                policyService.resolve(PolicyType.ACCESS_CONTROL, serviceId, apiId));
        GatewayPolicyCompiler.PolicyCompilation compiled = policyCompiler.apply(route, policies);
        compiled.circuitBreakerInstances().forEach((name, config) -> {
            if (circuitBreakers.putIfAbsent(name, config) != null) {
                throw new IllegalStateException("熔断器实例名冲突：" + name);
            }
        });
    }
}
