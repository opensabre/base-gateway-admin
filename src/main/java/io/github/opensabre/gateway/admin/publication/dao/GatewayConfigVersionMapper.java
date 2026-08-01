package io.github.opensabre.gateway.admin.publication.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayConfigVersion;
import org.apache.ibatis.annotations.Mapper;

/** 不可变网关配置版本持久化。 */
@Mapper
public interface GatewayConfigVersionMapper extends BaseMapper<GatewayConfigVersion> {
}
