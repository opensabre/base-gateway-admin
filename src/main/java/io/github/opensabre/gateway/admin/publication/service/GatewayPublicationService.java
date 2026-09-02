package io.github.opensabre.gateway.admin.publication.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.opensabre.gateway.admin.api.dao.GatewayApiMapper;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApiPublicationMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApplicationRouteMapper;
import io.github.opensabre.gateway.admin.publication.model.ApiPublicationChange;
import io.github.opensabre.gateway.admin.publication.model.ApplicationRouteChange;
import io.github.opensabre.gateway.admin.publication.model.ApprovalStatus;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import io.github.opensabre.gateway.admin.publication.model.PublicationStatus;
import io.github.opensabre.gateway.admin.publication.model.RiskLevel;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;
import io.github.opensabre.gateway.admin.route.model.GatewayRoute;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 保存 API 发布和应用路由草稿；真正生效由发布中心统一执行。 */
@Service
public class GatewayPublicationService {

    private static final TypeReference<List<GatewayRouteDefinition>> DEFINITIONS = new TypeReference<>() { };

    private final GatewayApiMapper apiMapper;
    private final GatewayApiPublicationMapper publicationMapper;
    private final GatewayApplicationRouteMapper applicationRouteMapper;
    private final GatewayRouteCompiler compiler;
    private final GatewayResourceBindingValidator resourceBindingValidator;
    private final ObjectMapper objectMapper;
    private final IGatewayRouteConfigService routeConfigService;

    public GatewayPublicationService(GatewayApiMapper apiMapper,
            GatewayApiPublicationMapper publicationMapper,
            GatewayApplicationRouteMapper applicationRouteMapper,
            GatewayRouteCompiler compiler,
            GatewayResourceBindingValidator resourceBindingValidator,
            ObjectMapper objectMapper,
            IGatewayRouteConfigService routeConfigService) {
        this.apiMapper = apiMapper;
        this.publicationMapper = publicationMapper;
        this.applicationRouteMapper = applicationRouteMapper;
        this.compiler = compiler;
        this.resourceBindingValidator = resourceBindingValidator;
        this.objectMapper = objectMapper;
        this.routeConfigService = routeConfigService;
    }

    /** 查询 API 发布声明。 */
    public List<GatewayApiPublication> listApiPublications() {
        List<GatewayApiPublication> publications = publicationMapper.selectList(
                new LambdaQueryWrapper<GatewayApiPublication>()
                        .orderByDesc(GatewayApiPublication::getUpdatedTime));
        if (publications.isEmpty()) {
            return List.of();
        }
        Map<String, GatewayApi> apiById = apiMapper.selectBatchIds(
                publications.stream().map(GatewayApiPublication::getApiId).toList()).stream()
                .collect(Collectors.toMap(GatewayApi::getId, Function.identity()));
        return publications.stream()
                .map(publication -> hydrateApiDetails(publication, apiById.get(publication.getApiId())))
                .toList();
    }

    /** 保存 API 发布草稿，不能借此接口直接改变线上状态。 */
    @Transactional
    public GatewayApiPublication saveApiDraft(String apiId, ApiPublicationChange change) {
        GatewayApi api = apiMapper.selectById(apiId);
        if (api == null) {
            throw new IllegalArgumentException("API 资产不存在：" + apiId);
        }
        GatewayApiPublication current = publicationMapper.selectOne(
                new LambdaQueryWrapper<GatewayApiPublication>().eq(GatewayApiPublication::getApiId, apiId));
        GatewayApiPublication draft = current == null ? new GatewayApiPublication() : current;
        if (current != null) {
            requireVersion(current, change.lockVersion());
        }
        draft.setApiId(apiId);
        draft.setExternalPath(change.externalPath());
        draft.setUpstreamPath(change.upstreamPath());
        draft.setFiltersJson(writeDefinitions(change.filters()));
        draft.setFilters(change.filters());
        draft.setAuthMode(change.authMode());
        draft.setResourceId(change.resourceId());
        draft.setStatus(PublicationStatus.DRAFT);
        draft.setRiskLevel(RiskLevel.LOW);
        draft.setApprovalStatus(ApprovalStatus.NOT_REQUIRED);
        draft.setLockVersion(current == null ? 0 : current.getLockVersion());
        resourceBindingValidator.validate(draft.getAuthMode(), draft.getResourceId(),
                api.getHttpMethod(), draft.getExternalPath());
        compiler.compile(List.of(new GatewayRouteCompiler.ApiPublicationCandidate(api, draft)), List.of());
        persist(publicationMapper, draft, current == null);
        return hydrateApiFilters(draft);
    }

    /** 标记 API 为待下线；正式发布前不会改变线上路由。 */
    @Transactional
    public GatewayApiPublication offlineApi(String apiId, Integer lockVersion) {
        GatewayApiPublication publication = publicationMapper.selectOne(
                new LambdaQueryWrapper<GatewayApiPublication>().eq(GatewayApiPublication::getApiId, apiId));
        if (publication == null) {
            throw new IllegalArgumentException("API 发布声明不存在：" + apiId);
        }
        requireVersion(publication, lockVersion);
        if (publication.getStatus() != PublicationStatus.PUBLISHED) {
            throw new IllegalStateException("只有已发布的 API 可以下线");
        }
        publication.setStatus(PublicationStatus.OFFLINE);
        // OFFLINE + publishedVersion=null 表示下线尚未进入正式配置版本。
        publication.setPublishedVersion(null);
        if (publicationMapper.updateById(publication) != 1) {
            throw new IllegalStateException("发布声明已被其他人修改，请刷新后重试");
        }
        return publicationMapper.selectById(publication.getId());
    }

    /** 查询应用级路由声明。 */
    public List<GatewayApplicationRoute> listApplicationRoutes() {
        return applicationRouteMapper.selectList(new LambdaQueryWrapper<GatewayApplicationRoute>()
                .orderByAsc(GatewayApplicationRoute::getServiceId)
                .orderByAsc(GatewayApplicationRoute::getExternalPath)).stream()
                .map(this::hydrateDefinitions)
                .toList();
    }

    /** 新建应用级路由草稿。 */
    @Transactional
    public GatewayApplicationRoute createApplicationDraft(ApplicationRouteChange change) {
        GatewayApplicationRoute draft = new GatewayApplicationRoute();
        apply(draft, change);
        draft.setLockVersion(0);
        compiler.compile(List.of(), List.of(draft));
        applicationRouteMapper.insert(draft);
        return draft;
    }

    /** 导入非托管运行时路由；旧 Route 仅在该草稿正式发布成功时原子移除。 */
    @Transactional
    public GatewayApplicationRoute adoptLegacyRoute(String routeId, String baseVersion) {
        if (routeId.startsWith("api-") || routeId.startsWith("application-")) {
            throw new IllegalArgumentException("该路由已经由控制面托管");
        }
        var snapshot = routeConfigService.getCurrentConfig();
        if (!snapshot.getVersion().equals(baseVersion)) {
            throw new IllegalStateException("网关配置已变化，请刷新后重新导入");
        }
        GatewayRoute source = snapshot.getRoutes().stream().filter(route -> routeId.equals(route.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("遗留路由不存在：" + routeId));
        Long imported = applicationRouteMapper.selectCount(new LambdaQueryWrapper<GatewayApplicationRoute>()
                .eq(GatewayApplicationRoute::getLegacyRouteId, routeId));
        if (imported != null && imported > 0) {
            throw new IllegalStateException("遗留路由已经存在纳管草稿：" + routeId);
        }
        String path = definitionValue(source.getPredicates(), "Path", "pattern", "patterns", "value");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("只有包含 Path 断言的路由可以纳管");
        }
        String method = definitionValue(source.getPredicates(), "Method", "method", "methods", "value");
        String serviceId = source.getUri().startsWith("lb://") ? source.getUri().substring(5) : source.getId();
        GatewayApplicationRoute draft = new GatewayApplicationRoute();
        apply(draft, new ApplicationRouteChange(serviceId, source.getId(), path, source.getUri(), method,
                null, source.getOrder(), source.getPredicates(), source.getFilters(), 0));
        draft.setLegacyRouteId(routeId);
        draft.setLockVersion(0);
        compiler.compile(List.of(), List.of(draft));
        if (applicationRouteMapper.insert(draft) != 1) {
            throw new IllegalStateException("创建遗留路由纳管草稿失败");
        }
        return hydrateDefinitions(draft);
    }

    private static String definitionValue(List<GatewayRouteDefinition> definitions, String name, String... keys) {
        if (definitions == null) return null;
        return definitions.stream().filter(value -> name.equals(value.getName())).findFirst()
                .map(value -> {
                    for (String key : keys) {
                        String result = value.getArgs() == null ? null : value.getArgs().get(key);
                        if (result != null && !result.isBlank()) return result;
                    }
                    if ("Path".equals(name) && value.getArgs() != null) {
                        String indexed = value.getArgs().get("patterns.0");
                        if (indexed != null && !indexed.isBlank()) return indexed;
                    }
                    return value.getArgs() == null || value.getArgs().isEmpty()
                            ? null : value.getArgs().values().iterator().next();
                }).orElse(null);
    }

    /** 修改尚未发布的应用级路由草稿。 */
    @Transactional
    public GatewayApplicationRoute updateApplicationDraft(String id, ApplicationRouteChange change) {
        GatewayApplicationRoute draft = applicationRouteMapper.selectById(id);
        if (draft == null) {
            throw new IllegalArgumentException("应用路由不存在：" + id);
        }
        requireVersion(draft, change.lockVersion());
        apply(draft, change);
        compiler.compile(List.of(), List.of(draft));
        if (applicationRouteMapper.updateById(draft) != 1) {
            throw new IllegalStateException("应用路由已被其他人修改，请刷新后重试");
        }
        return hydrateDefinitions(applicationRouteMapper.selectById(id));
    }

    /** 标记应用级路由为待下线；正式发布前不会改变线上路由。 */
    @Transactional
    public GatewayApplicationRoute offlineApplicationRoute(String id, Integer lockVersion) {
        GatewayApplicationRoute route = applicationRouteMapper.selectById(id);
        if (route == null) {
            throw new IllegalArgumentException("应用路由不存在：" + id);
        }
        requireVersion(route, lockVersion);
        if (route.getStatus() != PublicationStatus.PUBLISHED) {
            throw new IllegalStateException("只有已发布的应用路由可以下线");
        }
        route.setStatus(PublicationStatus.OFFLINE);
        route.setPublishedVersion(null);
        if (applicationRouteMapper.updateById(route) != 1) {
            throw new IllegalStateException("应用路由已被其他人修改，请刷新后重试");
        }
        return hydrateDefinitions(applicationRouteMapper.selectById(id));
    }

    private void apply(GatewayApplicationRoute draft, ApplicationRouteChange change) {
        draft.setServiceId(change.serviceId());
        draft.setRouteName(change.routeName());
        draft.setExternalPath(change.externalPath());
        draft.setTargetUri(change.targetUri());
        draft.setHttpMethod(change.httpMethod());
        draft.setRewritePath(change.rewritePath());
        draft.setRouteOrder(change.routeOrder() == null ? 100 : change.routeOrder());
        draft.setPredicatesJson(writeDefinitions(change.predicates()));
        draft.setFiltersJson(writeDefinitions(change.filters()));
        draft.setPredicates(change.predicates());
        draft.setFilters(change.filters());
        draft.setStatus(PublicationStatus.DRAFT);
        draft.setRiskLevel(compiler.classifyApplicationRisk(change.externalPath(), change.httpMethod()));
        draft.setApprovalStatus(ApprovalStatus.NOT_REQUIRED);
    }

    private String writeDefinitions(List<GatewayRouteDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(definitions);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("路由断言或过滤器格式错误", exception);
        }
    }

    private GatewayApplicationRoute hydrateDefinitions(GatewayApplicationRoute route) {
        route.setPredicates(readDefinitions(route.getPredicatesJson()));
        route.setFilters(readDefinitions(route.getFiltersJson()));
        return route;
    }

    private GatewayApiPublication hydrateApiFilters(GatewayApiPublication publication) {
        publication.setFilters(readDefinitions(publication.getFiltersJson()));
        return publication;
    }

    private GatewayApiPublication hydrateApiDetails(GatewayApiPublication publication, GatewayApi api) {
        hydrateApiFilters(publication);
        if (api != null) {
            publication.setServiceId(api.getServiceId());
            publication.setOperationId(api.getOperationId());
            publication.setHttpMethod(api.getHttpMethod());
            publication.setApiSummary(api.getSummary());
        }
        return publication;
    }

    private List<GatewayRouteDefinition> readDefinitions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, DEFINITIONS);
        } catch (JacksonException exception) {
            throw new IllegalStateException("已保存的路由断言或过滤器无法解析", exception);
        }
    }

    private void requireVersion(GatewayApiPublication current, Integer version) {
        if (version == null || !version.equals(current.getLockVersion())) {
            throw new IllegalStateException("发布声明已被其他人修改，请刷新后重试");
        }
    }

    private void requireVersion(GatewayApplicationRoute current, Integer version) {
        if (version == null || !version.equals(current.getLockVersion())) {
            throw new IllegalStateException("应用路由已被其他人修改，请刷新后重试");
        }
    }

    private void persist(GatewayApiPublicationMapper mapper, GatewayApiPublication value, boolean insert) {
        int affected = insert ? mapper.insert(value) : mapper.updateById(value);
        if (affected != 1) {
            throw new IllegalStateException("发布声明已被其他人修改，请刷新后重试");
        }
    }
}
