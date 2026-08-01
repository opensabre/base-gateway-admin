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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 保存 API 发布和应用路由草稿；真正生效由发布中心统一执行。 */
@Service
public class GatewayPublicationService {

    private final GatewayApiMapper apiMapper;
    private final GatewayApiPublicationMapper publicationMapper;
    private final GatewayApplicationRouteMapper applicationRouteMapper;
    private final GatewayRouteCompiler compiler;
    private final GatewayResourceBindingValidator resourceBindingValidator;

    public GatewayPublicationService(GatewayApiMapper apiMapper,
            GatewayApiPublicationMapper publicationMapper,
            GatewayApplicationRouteMapper applicationRouteMapper,
            GatewayRouteCompiler compiler,
            GatewayResourceBindingValidator resourceBindingValidator) {
        this.apiMapper = apiMapper;
        this.publicationMapper = publicationMapper;
        this.applicationRouteMapper = applicationRouteMapper;
        this.compiler = compiler;
        this.resourceBindingValidator = resourceBindingValidator;
    }

    /** 查询 API 发布声明。 */
    public List<GatewayApiPublication> listApiPublications() {
        return publicationMapper.selectList(new LambdaQueryWrapper<GatewayApiPublication>()
                .orderByDesc(GatewayApiPublication::getUpdatedTime));
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
        return draft;
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
                .orderByAsc(GatewayApplicationRoute::getExternalPath));
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
        return applicationRouteMapper.selectById(id);
    }

    private void apply(GatewayApplicationRoute draft, ApplicationRouteChange change) {
        draft.setServiceId(change.serviceId());
        draft.setRouteName(change.routeName());
        draft.setExternalPath(change.externalPath());
        draft.setTargetUri(change.targetUri());
        draft.setHttpMethod(change.httpMethod());
        draft.setRewritePath(change.rewritePath());
        draft.setStatus(PublicationStatus.DRAFT);
        draft.setRiskLevel(compiler.classifyApplicationRisk(change.externalPath(), change.httpMethod()));
        draft.setApprovalStatus(ApprovalStatus.NOT_REQUIRED);
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
