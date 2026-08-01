package io.github.opensabre.gateway.admin.publication.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 一次 Nacos CAS 发布的执行记录。 */
@Data
@TableName("base_gateway_admin_release")
@EqualsAndHashCode(callSuper = true)
public class GatewayRelease extends BasePo {
    private String draftId;
    private String targetVersion;
    private GatewayReleaseStatus status;
    private String failureReason;
    private Date startedTime;
    private Date completedTime;
}
