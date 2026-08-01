package io.github.opensabre.gateway.admin.api.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.api.model.GatewayApi;
import org.apache.ibatis.annotations.Mapper;

/** API 资产数据访问接口。 */
@Mapper
public interface GatewayApiMapper extends BaseMapper<GatewayApi> {
}
