package io.github.opensabre.gateway.admin.publication.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayApplicationRoute;
import org.apache.ibatis.annotations.Mapper;

/** 应用级路由数据访问接口。 */
@Mapper
public interface GatewayApplicationRouteMapper extends BaseMapper<GatewayApplicationRoute> {
}
