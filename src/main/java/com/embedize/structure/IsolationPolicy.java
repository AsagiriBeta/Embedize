package com.embedize.structure;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Whitelist-only isolation: managed structures generate ONLY in {@code allowed-worlds}.
 * Every other world is denied. Unmanaged namespaces are left alone ({@link Decision#PASS}).
 */
public final class IsolationPolicy {

    public enum StructureFilterMode {
        /** Only configured namespaces (e.g. nova_structures, my_pack). */
        NAMESPACES,
        /** Every structure whose namespace is not {@code minecraft}. */
        ALL_NON_MINECRAFT,
        /** Every structure including vanilla (rarely wanted). */
        ALL
    }

    public enum Decision {
        /** Not managed by Embedize — leave vanilla/other plugins alone. */
        PASS,
        /** Managed and world is on the whitelist — let generation proceed. */
        ALLOW,
        /** Managed and world is not on the whitelist — cancel generation. */
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
        this.namespaces = Set.copyOf(normalize(namespaces));
        this.denyUnresolvedKeys = denyUnresolvedKeys;
    }

    public Decision decide(String worldName, String namespace, String key) {
        if (!enabled) {
            return Decision.PASS;
        }
        if (namespace == null || namespace.isBlank()) {
            return denyUnresolvedKeys ? Decision.DENY : Decision.PASS;
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

    /**
     * Whitelist check with Multiverse alias resolution already folded into {@code listed}.
     */
    public Decision decideWithFlags(String namespace, String key, boolean listed) {
        if (namespace == null || namespace.isBlank()) {
            return denyUnresolvedKeys ? Decision.DENY : Decision.PASS;
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
        if (ns == null) {
            return false;
        }
        return switch (filterMode) {
            case ALL -> true;
            case ALL_NON_MINECRAFT -> !"minecraft".equals(ns);
            case NAMESPACES -> namespaces.contains(ns);
        };
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
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
