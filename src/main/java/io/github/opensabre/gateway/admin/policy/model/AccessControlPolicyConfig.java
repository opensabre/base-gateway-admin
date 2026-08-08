package io.github.opensabre.gateway.admin.policy.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Effective IP allowlist or denylist configuration. */
public record AccessControlPolicyConfig(
        @NotNull AccessMode accessMode,
        @NotNull @Size(min = 1, max = 20) List<@Valid Entry> entries) {

    public enum AccessMode { ALLOWLIST, DENYLIST }

    public record Entry(@NotBlank @Size(max = 64) String cidr,
                        @Size(max = 100) String description) {
    }

    @AssertTrue(message = "包含非法或重复的 IP/CIDR")
    public boolean isEntriesValid() {
        if (entries == null) return true;
        Set<String> unique = new HashSet<>();
        for (Entry entry : entries) {
            if (entry == null || entry.cidr() == null) continue;
            String cidr = entry.cidr().trim().toLowerCase(Locale.ROOT);
            if (!isCidr(cidr) || !unique.add(cidr)) return false;
        }
        return true;
    }

    private boolean isCidr(String value) {
        try {
            String[] parts = value.split("/", -1);
            if (parts.length > 2 || parts[0].isBlank() || !parts[0].matches("[0-9a-fA-F:.]+")) return false;
            byte[] address = InetAddress.getByName(parts[0]).getAddress();
            int prefix = parts.length == 1 ? address.length * Byte.SIZE : Integer.parseInt(parts[1]);
            return prefix >= 0 && prefix <= address.length * Byte.SIZE;
        } catch (Exception exception) {
            return false;
        }
    }
}
