package io.github.opensabre.gateway.admin;

import io.github.opensabre.governance.audit.annotations.EnabledAudit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * OpenSabre 网关控制面应用入口。
 */
@EnabledAudit
@ConfigurationPropertiesScan
@SpringBootApplication
public class GatewayAdminApplication {

    /** 启动独立网关控制面。 */
    public static void main(String[] args) {
        SpringApplication.run(GatewayAdminApplication.class, args);
    }
}
