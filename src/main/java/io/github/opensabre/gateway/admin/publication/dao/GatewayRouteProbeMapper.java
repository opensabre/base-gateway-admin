package io.github.opensabre.gateway.admin.publication.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayRouteProbe;
import org.apache.ibatis.annotations.Mapper;

/** 发布后路由装载探测持久化。 */
@Mapper
public interface GatewayRouteProbeMapper extends BaseMapper<GatewayRouteProbe> {
}
