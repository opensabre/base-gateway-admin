package io.github.opensabre.gateway.admin.publication.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.opensabre.gateway.admin.route.model.GatewayRouteDefinition;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/** 服务级通配路由声明。 */
@Data
@TableName("base_gateway_admin_application_route")
@EqualsAndHashCode(callSuper = true)
public class GatewayApplicationRoute extends BasePo {
    private String serviceId;
    private String routeName;
    private String externalPath;
    private String targetUri;
    private String httpMethod;
    private String rewritePath;
    private Integer routeOrder;
    @JsonIgnore
    private String predicatesJson;
    @JsonIgnore
    private String filtersJson;
    @TableField(exist = false)
    private List<GatewayRouteDefinition> predicates;
    @TableField(exist = false)
    private List<GatewayRouteDefinition> filters;
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
