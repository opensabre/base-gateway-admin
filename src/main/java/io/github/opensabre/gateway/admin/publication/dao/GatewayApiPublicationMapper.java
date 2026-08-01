package io.github.opensabre.gateway.admin.publication.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayApiPublication;
import org.apache.ibatis.annotations.Mapper;

/** API 发布声明数据访问接口。 */
@Mapper
public interface GatewayApiPublicationMapper extends BaseMapper<GatewayApiPublication> {
}
