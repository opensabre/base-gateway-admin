package io.github.opensabre.gateway.admin.config;

import io.github.opensabre.webmvc.interceptor.UserInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewayAuditWebMvcConfigurationTest {

    @Test
    void shouldRegisterSharedUserContextInterceptorForAuditedRequests() {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);

        new GatewayAuditWebMvcConfiguration().addInterceptors(registry);

        verify(registry).addInterceptor(isA(UserInterceptor.class));
    }
}
