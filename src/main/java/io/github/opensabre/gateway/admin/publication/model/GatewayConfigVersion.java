package io.github.opensabre.gateway.admin.publication.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.opensabre.persistence.entity.po.BasePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 发布成功后保存的不可变 Nacos 配置快照。 */
@Data
@TableName("base_gateway_admin_config_version")
@EqualsAndHashCode(callSuper = true)
public class GatewayConfigVersion extends BasePo {
    private String version;
    private String content;
    private String sourceVersion;
}
