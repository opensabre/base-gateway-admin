package io.github.opensabre.gateway.admin.publication.model;

import jakarta.validation.constraints.NotNull;

/** 将已保存的路由声明标记为待下线。 */
public record OfflineRequest(@NotNull Integer lockVersion) {
}
