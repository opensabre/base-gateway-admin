package io.github.opensabre.gateway.admin.api.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 可独立发布的网关 API 资产。 */
@Data
@TableName("base_gateway_admin_api")
@EqualsAndHashCode(callSuper = true)
public class GatewayApi extends BasePo {

    private String serviceId;
    private String productCode;
    private String operationId;
    private String httpMethod;
    private String upstreamPath;
    private String summary;
    private String tagsJson;
    private ApiSourceType sourceType;
    private String sourceHash;
    private ApiDiscoveryStatus discoveryStatus;
    private Date lastDiscoveredTime;
}
