package io.github.opensabre.gateway.admin.config;

import io.github.opensabre.webmvc.interceptor.UserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Binds the authenticated gateway request identity before audited controller methods execute.
 * The gateway has already authenticated the forwarded bearer token; the shared interceptor
 * extracts its subject into {@code UserContextHolder} and clears it after the request.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayAuditWebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInterceptor());
    }
}
