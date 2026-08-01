package io.github.opensabre.gateway.admin.publication.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayRelease;
import org.apache.ibatis.annotations.Mapper;

/** 网关发布执行记录持久化。 */
@Mapper
public interface GatewayReleaseMapper extends BaseMapper<GatewayRelease> {
}
