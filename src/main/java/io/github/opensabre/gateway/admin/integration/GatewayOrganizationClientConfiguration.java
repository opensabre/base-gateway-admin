package io.github.opensabre.gateway.admin.integration;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/** 注册网关控制面专用的组织资源只读客户端。 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = OrganizationResourceClient.class)
public class GatewayOrganizationClientConfiguration {
}
