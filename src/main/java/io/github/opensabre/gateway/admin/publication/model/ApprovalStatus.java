package io.github.opensabre.gateway.admin.publication.model;

/** 通配路由审批预留状态；当前版本固定为 NOT_REQUIRED。 */
public enum ApprovalStatus {
    NOT_REQUIRED,
    PENDING,
    APPROVED,
    REJECTED
}
