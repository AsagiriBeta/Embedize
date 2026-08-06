package com.embedize.structure;

import com.embedize.group.StructureGroup;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Isolation decisions for grouped datapack structures.
 * Vanilla {@code minecraft:} is never managed.
 */
public final class IsolationPolicy {

    public static final String VANILLA_NAMESPACE = "minecraft";

    public enum Decision {
        PASS,
        ALLOW,
        DENY
    }

    private final boolean enabled;
    private final boolean denyUnresolvedKeys;
    private final boolean manageUngrouped;
    private final GroupWorldResolver worldResolver;

    public interface GroupWorldResolver {
        Optional<StructureGroup> findGroup(String namespace);

        boolean isWorldAllowed(StructureGroup group, String worldName);
    }

    public IsolationPolicy(
            boolean enabled,
            boolean denyUnresolvedKeys,
            boolean manageUngrouped,
            GroupWorldResolver worldResolver
    ) {
        this.enabled = enabled;
        this.denyUnresolvedKeys = denyUnresolvedKeys;
        this.manageUngrouped = manageUngrouped;
        this.worldResolver = worldResolver;
    }

    public Decision decide(String worldName, String namespace, String key) {
        if (!enabled) {
            return Decision.PASS;
        }
        if (namespace == null || namespace.isBlank()) {
            return denyUnresolvedKeys ? Decision.DENY : Decision.PASS;
        }
        if (isVanillaNamespace(namespace)) {
            return Decision.PASS;
        }

        Optional<StructureGroup> groupOpt = worldResolver.findGroup(namespace);
        if (groupOpt.isEmpty()) {
            // Namespace not assigned to any group
            return manageUngrouped ? Decision.DENY : Decision.PASS;
        }

        StructureGroup group = groupOpt.get();
        if (worldName == null || worldName.isBlank()) {
            return Decision.DENY;
        }
        return worldResolver.isWorldAllowed(group, worldName) ? Decision.ALLOW : Decision.DENY;
    }

    public Decision decideWithFlags(String namespace, boolean worldAllowedForGroup, boolean hasGroup) {
        if (namespace == null || namespace.isBlank()) {
            return denyUnresolvedKeys ? Decision.DENY : Decision.PASS;
        }
        if (isVanillaNamespace(namespace)) {
            return Decision.PASS;
        }
        if (!hasGroup) {
            return manageUngrouped ? Decision.DENY : Decision.PASS;
        }
        return worldAllowedForGroup ? Decision.ALLOW : Decision.DENY;
    }

    public static boolean isVanillaNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return false;
        }
        return VANILLA_NAMESPACE.equals(namespace.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isManageUngrouped() {
        return manageUngrouped;
    }

    public static boolean anyGroupAllows(Collection<StructureGroup> groups, String worldName, GroupWorldResolver resolver) {
        for (StructureGroup group : groups) {
            if (resolver.isWorldAllowed(group, worldName)) {
                return true;
            }
        }
        return false;
    }
}
