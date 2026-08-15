package io.github.opensabre.gateway.admin;

import com.alibaba.cloud.nacos.NacosConfigManager;
import io.github.opensabre.governance.audit.annotations.Audit;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证网关控制面基础自动装配可以完整启动。
 */
@SpringBootTest
class GatewayAdminApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private NacosConfigManager nacosConfigManager;

    @Test
    void contextLoads() {
        // Spring 上下文成功启动即完成验证。
    }

    @Test
    void everyCommandEndpointMustDeclareAnAuditRecord() {
        applicationContext.getBeansWithAnnotation(RestController.class).values().stream()
                .map(AopUtils::getTargetClass)
                .filter(type -> type.getPackageName().startsWith("io.github.opensabre.gateway.admin"))
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(GatewayAdminApplicationTest::isCommandEndpoint)
                .forEach(method -> assertThat(method.getAnnotation(Audit.class))
                        .as("command endpoint %s#%s must be audited",
                                method.getDeclaringClass().getSimpleName(), method.getName())
                        .isNotNull());
    }

    private static boolean isCommandEndpoint(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}
