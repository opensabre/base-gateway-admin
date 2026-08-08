package io.github.opensabre.gateway.admin.policy.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlPolicyConfigTest {

    @Test
    void acceptsIpv4AndIpv6Cidrs() {
        var config = new AccessControlPolicyConfig(AccessControlPolicyConfig.AccessMode.ALLOWLIST, List.of(
                new AccessControlPolicyConfig.Entry("10.0.0.0/8", "private network"),
                new AccessControlPolicyConfig.Entry("2001:db8::/32", "IPv6 network")));

        assertThat(config.isEntriesValid()).isTrue();
    }

    @Test
    void rejectsHostnamesAndDuplicateEntries() {
        var hostname = new AccessControlPolicyConfig(AccessControlPolicyConfig.AccessMode.DENYLIST,
                List.of(new AccessControlPolicyConfig.Entry("example.com", null)));
        var duplicate = new AccessControlPolicyConfig(AccessControlPolicyConfig.AccessMode.DENYLIST, List.of(
                new AccessControlPolicyConfig.Entry("192.0.2.1", null),
                new AccessControlPolicyConfig.Entry("192.0.2.1", "duplicate")));

        assertThat(hostname.isEntriesValid()).isFalse();
        assertThat(duplicate.isEntriesValid()).isFalse();
    }
}
