package io.github.opensabre.gateway.admin.publication.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;

import java.util.Date;
import java.util.List;

/** API 资产的独立外部发布声明。 */
@Data
@TableName("base_gateway_admin_api_publication")
@EqualsAndHashCode(callSuper = true)
public class GatewayApiPublication extends BasePo {
    private String apiId;
    private String externalPath;
    private String upstreamPath;
    @JsonIgnore
    private String filtersJson;
    @TableField(exist = false)
    private List<GatewayRouteDefinition> filters;
    @TableField(exist = false)
    private String serviceId;
    @TableField(exist = false)
    private String operationId;
    @TableField(exist = false)
    private String httpMethod;
    @TableField(exist = false)
    private String apiSummary;
    private AuthMode authMode;
    private String resourceId;
    private PublicationStatus status;
    private RiskLevel riskLevel;
    private ApprovalStatus approvalStatus;
    private String approvalReason;
    private String approvedBy;
    private Date approvedTime;
    private String publishedVersion;
    @Version
    private Integer lockVersion;
}
