package io.github.opensabre.gateway.admin.publication.service;

import io.github.opensabre.gateway.admin.api.dao.GatewayApiMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApiPublicationMapper;
import io.github.opensabre.gateway.admin.publication.dao.GatewayApplicationRouteMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import io.github.opensabre.gateway.admin.publication.model.PublicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayPublicationServiceTest {

    private final GatewayApiMapper apiMapper = mock(GatewayApiMapper.class);
    private final GatewayApiPublicationMapper publicationMapper = mock(GatewayApiPublicationMapper.class);
    private final GatewayApplicationRouteMapper applicationRouteMapper = mock(GatewayApplicationRouteMapper.class);
    private final GatewayRouteCompiler compiler = mock(GatewayRouteCompiler.class);
    private final GatewayResourceBindingValidator resourceValidator = mock(GatewayResourceBindingValidator.class);
    private GatewayPublicationService service;

    @BeforeEach
    void setUp() {
        service = new GatewayPublicationService(apiMapper, publicationMapper, applicationRouteMapper,
                compiler, resourceValidator, new ObjectMapper());
    }

    @Test
    void shouldCreateOfflineDraftWithoutChangingRuntimeDirectly() {
        GatewayApiPublication published = publication(PublicationStatus.PUBLISHED);
        published.setPublishedVersion("version-1");
        when(publicationMapper.selectOne(any())).thenReturn(published);
        when(publicationMapper.updateById(any())).thenReturn(1);
        when(publicationMapper.selectById("publication-1")).thenAnswer(ignored -> published);

        GatewayApiPublication result = service.offlineApi("api-1", 3);

        assertThat(result.getStatus()).isEqualTo(PublicationStatus.OFFLINE);
        assertThat(result.getPublishedVersion()).isNull();
    }

    @Test
    void shouldRejectOfflineForUnpublishedDraft() {
        when(publicationMapper.selectOne(any())).thenReturn(publication(PublicationStatus.DRAFT));

        assertThatIllegalStateException().isThrownBy(() -> service.offlineApi("api-1", 3))
                .withMessageContaining("只有已发布");
    }

    private GatewayApiPublication publication(PublicationStatus status) {
        GatewayApiPublication publication = new GatewayApiPublication();
        publication.setId("publication-1");
        publication.setApiId("api-1");
        publication.setStatus(status);
        publication.setLockVersion(3);
        return publication;
    }
}
