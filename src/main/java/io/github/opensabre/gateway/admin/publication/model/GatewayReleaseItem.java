package io.github.opensabre.gateway.admin.publication.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 一次发布实际包含的 API、应用路由或回滚来源。 */
@Data
@TableName("base_gateway_admin_release_item")
public class GatewayReleaseItem {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String releaseId;
    private String itemType;
    private String itemId;
    private String changeType;
    private String summary;
}
