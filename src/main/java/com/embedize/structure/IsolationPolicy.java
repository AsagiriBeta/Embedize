package com.embedize.structure;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whitelist-only isolation for datapack structures.
 * Vanilla {@code minecraft:} structures are never managed.
 */
public final class IsolationPolicy {

    public static final String VANILLA_NAMESPACE = "minecraft";

    public enum StructureFilterMode {
        /** Only configured namespaces (e.g. nova_structures). */
        NAMESPACES,
        /** Every non-minecraft namespace. */
        ALL_NON_MINECRAFT
    }

    public enum Decision {
        /** Not managed — Embedize does not cancel (vanilla / unmanaged packs). */
        PASS,
        /** Managed and world is on the whitelist. */
        ALLOW,
        /** Managed and world is not on the whitelist — cancel. */
        DENY
    }

    private final boolean enabled;
    private final StructureFilterMode filterMode;
    private final Set<String> allowedWorlds;
    private final Set<String> namespaces;
    private final boolean denyUnresolvedKeys;

    public IsolationPolicy(
            boolean enabled,
            StructureFilterMode filterMode,
            Set<String> allowedWorlds,
            Set<String> namespaces,
            boolean denyUnresolvedKeys
    ) {
        this.enabled = enabled;
        this.filterMode = Objects.requireNonNull(filterMode);
        this.allowedWorlds = Set.copyOf(normalize(allowedWorlds));
        this.namespaces = Set.copyOf(sanitizeNamespaces(namespaces));
        this.denyUnresolvedKeys = denyUnresolvedKeys;
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
        if (!isManagedNamespace(namespace)) {
            return Decision.PASS;
        }
        String world = normalizeOne(worldName);
        if (world == null) {
            return Decision.DENY;
        }
        return allowedWorlds.contains(world) ? Decision.ALLOW : Decision.DENY;
    }

    public Decision decideWithFlags(String namespace, String key, boolean listed) {
        if (namespace == null || namespace.isBlank()) {
            return denyUnresolvedKeys ? Decision.DENY : Decision.PASS;
        }
        if (isVanillaNamespace(namespace)) {
            return Decision.PASS;
        }
        if (!isManagedNamespace(namespace)) {
            return Decision.PASS;
        }
        if (allowedWorlds.isEmpty()) {
            return Decision.DENY;
        }
        return listed ? Decision.ALLOW : Decision.DENY;
    }

    public boolean isManagedNamespace(String namespace) {
        String ns = normalizeOne(namespace);
        if (ns == null || isVanillaNamespace(ns)) {
            return false;
        }
        return switch (filterMode) {
            case ALL_NON_MINECRAFT -> true;
            case NAMESPACES -> namespaces.contains(ns);
        };
    }

    public static boolean isVanillaNamespace(String namespace) {
        return VANILLA_NAMESPACE.equals(normalizeOne(namespace));
    }

    public boolean isAllowedWorldListed(String worldName) {
        String world = normalizeOne(worldName);
        return world != null && allowedWorlds.contains(world);
    }

    public StructureFilterMode getFilterMode() {
        return filterMode;
    }

    public Set<String> getAllowedWorlds() {
        return allowedWorlds;
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    private static Set<String> sanitizeNamespaces(Collection<String> values) {
        return normalize(values).stream()
                .filter(ns -> !VANILLA_NAMESPACE.equals(ns))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String normalizeOne(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalize(Collection<String> values) {
        return values.stream()
                .map(IsolationPolicy::normalizeOne)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
