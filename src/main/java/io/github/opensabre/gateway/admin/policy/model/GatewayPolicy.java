package io.github.opensabre.gateway.admin.policy.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分层治理策略的持久化记录。 */
@Data
@TableName("base_gateway_admin_policy")
@EqualsAndHashCode(callSuper = true)
public class GatewayPolicy extends BasePo {

    private PolicyScopeType scopeType;
    private String scopeId;
    private PolicyType policyType;
    private PolicyMode mode;
    private String configJson;

    @Version
    private Integer lockVersion;
}
