package io.github.opensabre.gateway.admin.publication.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApiPublicationMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApplicationRouteMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayConfigVersionMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayReleaseMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayReleaseItemMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayInstanceRevisionMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayRouteProbeMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import io.github.opensabre.gateway.admin.publication.model.GatewayConfigVersion;
import io.github.opensabre.gateway.admin.publication.model.GatewayRelease;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseResult;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseDetail;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseItem;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceRevision;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceVerification;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbe;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbeSummary;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseStatus;
import io.github.opensabre.gateway.admin.publication.model.PublicationStatus;
import io.github.opensabre.gateway.admin.publication.model.ReleaseValidationResult;
import io.github.opensabre.gateway.admin.route.model.GatewayManagedPublishResult;
import io.github.opensabre.gateway.admin.route.service.IGatewayRouteConfigService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/** 执行 API/应用路由候选的 Nacos CAS 正式发布并记录不可变版本。 */
@Service
public class GatewayReleaseService {

    private final GatewayReleaseValidationService validationService;
    private final IGatewayRouteConfigService routeConfigService;
    private final GatewayReleaseMapper releaseMapper;
    private final GatewayConfigVersionMapper configVersionMapper;
    private final GatewayApiPublicationMapper apiPublicationMapper;
    private final GatewayApplicationRouteMapper applicationRouteMapper;
    private final GatewayReleaseItemMapper releaseItemMapper;
    private final GatewayInstanceRevisionMapper instanceRevisionMapper;
    private final GatewayInstanceVerificationService instanceVerificationService;
    private final GatewayRouteProbeMapper routeProbeMapper;
    private final GatewayRouteProbeService routeProbeService;

    public GatewayReleaseService(GatewayReleaseValidationService validationService,
            IGatewayRouteConfigService routeConfigService,
            GatewayReleaseMapper releaseMapper,
            GatewayConfigVersionMapper configVersionMapper,
            GatewayApiPublicationMapper apiPublicationMapper,
            GatewayApplicationRouteMapper applicationRouteMapper,
            GatewayReleaseItemMapper releaseItemMapper,
            GatewayInstanceRevisionMapper instanceRevisionMapper,
            GatewayInstanceVerificationService instanceVerificationService,
            GatewayRouteProbeMapper routeProbeMapper,
            GatewayRouteProbeService routeProbeService) {
        this.validationService = validationService;
        this.routeConfigService = routeConfigService;
        this.releaseMapper = releaseMapper;
        this.configVersionMapper = configVersionMapper;
        this.apiPublicationMapper = apiPublicationMapper;
        this.applicationRouteMapper = applicationRouteMapper;
        this.releaseItemMapper = releaseItemMapper;
        this.instanceRevisionMapper = instanceRevisionMapper;
        this.instanceVerificationService = instanceVerificationService;
        this.routeProbeMapper = routeProbeMapper;
        this.routeProbeService = routeProbeService;
    }

    /** 先执行同等强度预检，再以调用方基线执行 CAS 发布。 */
    public GatewayReleaseResult publish(String baseVersion) {
        ReleaseValidationResult candidate = validationService.validate(baseVersion);
        GatewayRelease release = startRelease();
        GatewayManagedPublishResult published = null;
        try {
            published = routeConfigService.publishManaged(baseVersion, release.getId(),
                    candidate.managedRoutes(), candidate.circuitBreakerInstances(), candidate.globalRules());
            saveImmutableVersion(published);
            saveManagedRouteItems(release.getId(), candidate);
            saveOfflineItems(release.getId());
            markDeclarationsPublished(published.targetVersion());
            GatewayReleaseStatus finalStatus = verifyRuntime(release.getId(), published.content());
            finishRelease(release, published.targetVersion(), finalStatus, null);
            return new GatewayReleaseResult(release.getId(), published.sourceVersion(), published.targetVersion(),
                    candidate.apiRouteCount(), candidate.applicationRouteCount(), release.getStatus());
        } catch (RuntimeException exception) {
            // Nacos 已成功后发生数据库异常时不能标记为普通失败，否则会误导运维人员认为配置未生效。
            GatewayReleaseStatus status = published == null ? GatewayReleaseStatus.FAILED
                    : GatewayReleaseStatus.RECONCILIATION_REQUIRED;
            finishRelease(release, published == null ? "FAILED" : published.targetVersion(),
                    status, abbreviate(exception.getMessage()));
            throw exception;
        }
    }

    /** 查询最近的发布记录。 */
    public List<GatewayRelease> list() {
        return releaseMapper.selectList(new LambdaQueryWrapper<GatewayRelease>()
                .orderByDesc(GatewayRelease::getStartedTime));
    }

    /** 查询单次发布记录。 */
    public GatewayReleaseDetail get(String id) {
        GatewayRelease release = releaseMapper.selectById(id);
        if (release == null) throw new IllegalArgumentException("发布记录不存在：" + id);
        List<GatewayReleaseItem> items = releaseItemMapper.selectList(
                new LambdaQueryWrapper<GatewayReleaseItem>()
                        .eq(GatewayReleaseItem::getReleaseId, id)
                        .orderByAsc(GatewayReleaseItem::getItemType)
                        .orderByAsc(GatewayReleaseItem::getItemId));
        List<GatewayInstanceRevision> instances = instanceRevisionMapper.selectList(
                new LambdaQueryWrapper<GatewayInstanceRevision>()
                        .eq(GatewayInstanceRevision::getReleaseId, id)
                        .orderByAsc(GatewayInstanceRevision::getInstanceId));
        List<GatewayRouteProbe> routeProbes = routeProbeMapper.selectList(
                new LambdaQueryWrapper<GatewayRouteProbe>()
                        .eq(GatewayRouteProbe::getReleaseId, id)
                        .orderByAsc(GatewayRouteProbe::getInstanceId));
        return new GatewayReleaseDetail(release, items, instances, routeProbes);
    }

    /** 将指定发布对应的不可变快照作为一次新的 CAS 发布执行。 */
    public GatewayReleaseResult rollback(String sourceReleaseId, String baseVersion) {
        GatewayRelease sourceRelease = releaseMapper.selectById(sourceReleaseId);
        if (sourceRelease == null) {
            throw new IllegalArgumentException("发布记录不存在：" + sourceReleaseId);
        }
        GatewayConfigVersion sourceVersion = configVersionMapper.selectOne(
                new LambdaQueryWrapper<GatewayConfigVersion>()
                        .eq(GatewayConfigVersion::getVersion, sourceRelease.getTargetVersion()));
        if (sourceVersion == null) {
            throw new IllegalStateException("发布记录缺少不可变配置版本，不能回滚：" + sourceReleaseId);
        }
        GatewayRelease rollback = startRelease();
        GatewayManagedPublishResult published = null;
        try {
            published = routeConfigService.publishSnapshot(baseVersion, rollback.getId(), sourceVersion.getContent());
            saveImmutableVersion(published);
            saveRollbackItem(rollback.getId(), sourceReleaseId, baseVersion, sourceVersion.getVersion());
            GatewayReleaseStatus finalStatus = verifyRuntime(rollback.getId(), published.content());
            finishRelease(rollback, published.targetVersion(), finalStatus, null);
            return new GatewayReleaseResult(rollback.getId(), published.sourceVersion(), published.targetVersion(),
                    0, 0, rollback.getStatus());
        } catch (RuntimeException exception) {
            GatewayReleaseStatus status = published == null ? GatewayReleaseStatus.FAILED
                    : GatewayReleaseStatus.RECONCILIATION_REQUIRED;
            finishRelease(rollback, published == null ? "FAILED" : published.targetVersion(),
                    status, abbreviate(exception.getMessage()));
            throw exception;
        }
    }

    /** 对部分生效的发布重新执行逐实例确认，并在全部加载后提升为成功。 */
    public GatewayInstanceVerification verifyInstances(String releaseId) {
        GatewayRelease release = releaseMapper.selectById(releaseId);
        if (release == null) throw new IllegalArgumentException("发布记录不存在：" + releaseId);
        if (release.getStatus() != GatewayReleaseStatus.PARTIALLY_APPLIED) {
            throw new IllegalStateException("只有部分生效的发布可以重新确认实例状态");
        }
        GatewayInstanceVerification verification = instanceVerificationService.verify(releaseId);
        if (verification.allLoaded()) {
            GatewayConfigVersion version = configVersionMapper.selectOne(
                    new LambdaQueryWrapper<GatewayConfigVersion>()
                            .eq(GatewayConfigVersion::getVersion, release.getTargetVersion()));
            if (version == null) throw new IllegalStateException("发布记录缺少不可变配置版本");
            GatewayRouteProbeSummary probes = routeProbeService.probe(releaseId,
                    routeConfigService.managedRouteIds(version.getContent()));
            if (probes.allPassed()) {
                finishRelease(release, release.getTargetVersion(), GatewayReleaseStatus.SUCCEEDED, null);
            }
        }
        return verification;
    }

    private GatewayReleaseStatus verifyRuntime(String releaseId, String content) {
        GatewayInstanceVerification instances = instanceVerificationService.verify(releaseId);
        if (!instances.allLoaded()) return GatewayReleaseStatus.PARTIALLY_APPLIED;
        GatewayRouteProbeSummary probes = routeProbeService.probe(releaseId,
                routeConfigService.managedRouteIds(content));
        return probes.allPassed() ? GatewayReleaseStatus.SUCCEEDED : GatewayReleaseStatus.PARTIALLY_APPLIED;
    }

    private GatewayRelease startRelease() {
        GatewayRelease release = new GatewayRelease();
        release.setTargetVersion("PENDING");
        release.setStatus(GatewayReleaseStatus.PUBLISHING);
        release.setStartedTime(new Date());
        if (releaseMapper.insert(release) != 1) {
            throw new IllegalStateException("创建网关发布记录失败");
        }
        return release;
    }

    private void saveImmutableVersion(GatewayManagedPublishResult published) {
        Long count = configVersionMapper.selectCount(new LambdaQueryWrapper<GatewayConfigVersion>()
                .eq(GatewayConfigVersion::getVersion, published.targetVersion()));
        if (count != null && count > 0) return;
        GatewayConfigVersion version = new GatewayConfigVersion();
        version.setVersion(published.targetVersion());
        version.setSourceVersion(published.sourceVersion());
        version.setContent(published.content());
        if (configVersionMapper.insert(version) != 1) {
            throw new IllegalStateException("保存不可变网关配置版本失败");
        }
    }

    private void markDeclarationsPublished(String version) {
        List<GatewayApiPublication> apiDrafts = apiPublicationMapper.selectList(
                new LambdaQueryWrapper<GatewayApiPublication>()
                        .eq(GatewayApiPublication::getStatus, PublicationStatus.DRAFT));
        for (GatewayApiPublication draft : apiDrafts) {
            draft.setStatus(PublicationStatus.PUBLISHED);
            draft.setPublishedVersion(version);
            if (apiPublicationMapper.updateById(draft) != 1) {
                throw new IllegalStateException("更新 API 发布状态失败：" + draft.getId());
            }
        }
        List<GatewayApiPublication> pendingOffline = apiPublicationMapper.selectList(
                new LambdaQueryWrapper<GatewayApiPublication>()
                        .eq(GatewayApiPublication::getStatus, PublicationStatus.OFFLINE));
        for (GatewayApiPublication offline : pendingOffline) {
            if (offline.getPublishedVersion() == null) {
                offline.setPublishedVersion(version);
                if (apiPublicationMapper.updateById(offline) != 1) {
                    throw new IllegalStateException("更新 API 下线版本失败：" + offline.getId());
                }
            }
        }
        List<GatewayApplicationRoute> applicationDrafts = applicationRouteMapper.selectList(
                new LambdaQueryWrapper<GatewayApplicationRoute>()
                        .eq(GatewayApplicationRoute::getStatus, PublicationStatus.DRAFT));
        for (GatewayApplicationRoute draft : applicationDrafts) {
            draft.setStatus(PublicationStatus.PUBLISHED);
            draft.setPublishedVersion(version);
            if (applicationRouteMapper.updateById(draft) != 1) {
                throw new IllegalStateException("更新应用路由发布状态失败：" + draft.getId());
            }
        }
    }

    private void saveOfflineItems(String releaseId) {
        List<GatewayApiPublication> offlineDeclarations = apiPublicationMapper.selectList(
                new LambdaQueryWrapper<GatewayApiPublication>()
                        .eq(GatewayApiPublication::getStatus, PublicationStatus.OFFLINE));
        offlineDeclarations.stream()
                .filter(value -> value.getPublishedVersion() == null)
                .forEach(value -> {
                    GatewayReleaseItem item = new GatewayReleaseItem();
                    item.setReleaseId(releaseId);
                    item.setItemType("API");
                    item.setItemId(value.getApiId());
                    item.setChangeType("OFFLINE");
                    item.setSummary(value.getExternalPath() + " offline");
                    if (releaseItemMapper.insert(item) != 1) {
                        throw new IllegalStateException("保存 API 下线影响项失败：" + value.getApiId());
                    }
                });
    }

    private void saveManagedRouteItems(String releaseId, ReleaseValidationResult candidate) {
        candidate.managedRoutes().forEach(route -> {
            GatewayReleaseItem item = new GatewayReleaseItem();
            item.setReleaseId(releaseId);
            item.setItemType(route.getId().startsWith("api-") ? "API" : "APPLICATION_ROUTE");
            item.setItemId(route.getId().replaceFirst("^(api|application)-", ""));
            item.setChangeType("PUBLISH");
            item.setSummary(route.getId() + " -> " + route.getUri());
            if (releaseItemMapper.insert(item) != 1) {
                throw new IllegalStateException("保存网关发布影响项失败：" + route.getId());
            }
        });
    }

    private void saveRollbackItem(String releaseId, String sourceReleaseId, String baseVersion, String version) {
        GatewayReleaseItem item = new GatewayReleaseItem();
        item.setReleaseId(releaseId);
        item.setItemType("CONFIG_VERSION");
        item.setItemId(version);
        item.setChangeType("ROLLBACK");
        item.setSummary("从 " + baseVersion + " 回滚到发布记录 " + sourceReleaseId + " 的版本 " + version);
        if (releaseItemMapper.insert(item) != 1) {
            throw new IllegalStateException("保存网关回滚影响项失败");
        }
    }

    private void finishRelease(GatewayRelease release, String targetVersion,
            GatewayReleaseStatus status, String failureReason) {
        release.setTargetVersion(targetVersion);
        release.setStatus(status);
        release.setFailureReason(failureReason);
        release.setCompletedTime(new Date());
        releaseMapper.updateById(release);
    }

    private String abbreviate(String message) {
        if (message == null) return "未知发布错误";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
