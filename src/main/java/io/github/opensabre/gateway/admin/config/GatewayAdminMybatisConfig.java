package io.github.opensabre.gateway.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.IllegalSQLInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关控制面需要乐观锁拦截器来维护 API、应用路由和策略的 lock_version。
 *
 * <p>基础持久化 starter 的默认拦截器未启用乐观锁；如果只在实体上声明 {@code @Version}，
 * MyBatis-Plus 会生成版本条件但不会补充原始版本参数，导致发布后更新声明失败。</p>
 */
@Configuration
public class GatewayAdminMybatisConfig {

    @Value("${opensabre.persistence.interceptor.blockattack.enabled:true}")
    private boolean blockAttackEnabled;

    @Value("${opensabre.persistence.interceptor.illegalsql.enabled:false}")
    private boolean illegalSqlEnabled;

    @Value("${opensabre.persistence.interceptor.pagination.enabled:true}")
    private boolean paginationEnabled;

    @Value("${opensabre.persistence.interceptor.pagination.dbType:mysql}")
    private String paginationDbType;

    /** 为网关控制面启用版本校验，并保留持久化 starter 的基础 SQL 防护插件。 */
    @Bean
    public MybatisPlusInterceptor gatewayAdminMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        if (paginationEnabled) {
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.getDbType(paginationDbType)));
        }
        if (blockAttackEnabled) {
            interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        }
        if (illegalSqlEnabled) {
            interceptor.addInnerInterceptor(new IllegalSQLInnerInterceptor());
        }
        return interceptor;
    }
}
