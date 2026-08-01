package io.github.opensabre.gateway.admin.publication.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 单个网关实例对本次托管 Route ID 集合的装载检查。 */
@Data
@TableName("base_gateway_admin_route_probe")
public class GatewayRouteProbe {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String releaseId;
    private String instanceId;
    private GatewayRouteProbeStatus status;
    private String missingRouteIdsJson;
    private String errorMessage;
    private Date probedTime;
}
