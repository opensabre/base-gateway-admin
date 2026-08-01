package io.github.opensabre.gateway.admin.publication.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 一次发布在单个网关实例上的加载确认结果。 */
@Data
@TableName("base_gateway_admin_instance_revision")
public class GatewayInstanceRevision {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String releaseId;
    private String instanceId;
    private String loadedVersion;
    private GatewayInstanceRevisionStatus status;
    private String errorMessage;
    private Date reportedTime;
}
