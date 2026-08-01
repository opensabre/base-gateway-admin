package io.github.opensabre.gateway.admin.publication.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.gateway.admin.publication.model.GatewayReleaseItem;
import org.apache.ibatis.annotations.Mapper;

/** 发布影响项持久化。 */
@Mapper
public interface GatewayReleaseItemMapper extends BaseMapper<GatewayReleaseItem> {
}
