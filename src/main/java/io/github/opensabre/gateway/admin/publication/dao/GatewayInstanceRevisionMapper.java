package io.github.opensabre.gateway.admin.publication.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayInstanceRevision;
import org.apache.ibatis.annotations.Mapper;

/** 网关实例加载确认结果持久化。 */
@Mapper
public interface GatewayInstanceRevisionMapper extends BaseMapper<GatewayInstanceRevision> {
}
