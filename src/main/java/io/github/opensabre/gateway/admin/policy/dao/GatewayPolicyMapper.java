package io.github.opensabre.gateway.admin.policy.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.policy.model.GatewayPolicy;
import org.apache.ibatis.annotations.Mapper;

/** 网关治理策略数据访问接口。 */
@Mapper
public interface GatewayPolicyMapper extends BaseMapper<GatewayPolicy> {
}
