package com.embedize.structure;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed isolation decisions. Pure logic — no Bukkit dependency.
 *
 * <p>ALLOWLIST + strict mode means: a managed structure may place blocks ONLY when the
 * world is explicitly allowed AND not sealed. Default/main worlds never receive managed
 * datapack structures unless the operator removes them from {@code sealed-worlds} and
 * also lists them in {@code allowed-worlds}.</p>
 */
public final class IsolationPolicy {

    public enum Mode {
        ALLOWLIST,
        DENYLIST
    }

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
        /** Managed and allowed in this world — let generation proceed. */
        ALLOW,
        /** Managed and forbidden in this world — cancel generation. */
        DENY
    }

    private final boolean enabled;
    private final boolean strict;
    private final Mode mode;
    private final StructureFilterMode filterMode;
    private final Set<String> allowedWorlds;
    private final Set<String> sealedWorlds;
    private final Set<String> namespaces;
    private final boolean denyUnresolvedKeys;

    public IsolationPolicy(
            boolean enabled,
            boolean strict,
            Mode mode,
            StructureFilterMode filterMode,
            Set<String> allowedWorlds,
            Set<String> sealedWorlds,
            Set<String> namespaces,
            boolean denyUnresolvedKeys
    ) {
        this.enabled = enabled;
        this.strict = strict;
        this.mode = Objects.requireNonNull(mode);
        this.filterMode = Objects.requireNonNull(filterMode);
        this.allowedWorlds = Set.copyOf(normalize(allowedWorlds));
        this.sealedWorlds = Set.copyOf(normalize(sealedWorlds));
        this.namespaces = Set.copyOf(normalize(namespaces));
        this.denyUnresolvedKeys = denyUnresolvedKeys;
    }

    public Decision decide(String worldName, String namespace, String key) {
        if (!enabled) {
            return Decision.PASS;
        }

        if (namespace == null || namespace.isBlank()) {
            // Unknown identity: in strict mode fail closed when configured to do so
            return (strict && denyUnresolvedKeys) ? Decision.DENY : Decision.PASS;
        }

        if (!isManagedNamespace(namespace)) {
            return Decision.PASS;
        }

        String world = normalizeOne(worldName);
        if (world == null) {
            return strict ? Decision.DENY : Decision.PASS;
        }

        // Sealed worlds are an absolute ban for managed structures (strict isolation).
        if (sealedWorlds.contains(world)) {
            return Decision.DENY;
        }

        boolean listed = allowedWorlds.contains(world);
        boolean allow = mode == Mode.ALLOWLIST ? listed : !listed;

        // Fail-closed: empty allowlist in ALLOWLIST mode denies everywhere (except PASS for unmanaged).
        if (mode == Mode.ALLOWLIST && allowedWorlds.isEmpty()) {
            allow = false;
        }

        // Strict ALLOWLIST: must be explicitly listed; no fuzzy fallback.
        if (strict && mode == Mode.ALLOWLIST && !listed) {
            allow = false;
        }

        return allow ? Decision.ALLOW : Decision.DENY;
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

    public boolean isSealedWorld(String worldName) {
        String world = normalizeOne(worldName);
        return world != null && sealedWorlds.contains(world);
    }

    public boolean isAllowedWorldListed(String worldName) {
        String world = normalizeOne(worldName);
        return world != null && allowedWorlds.contains(world);
    }

    public Mode getMode() {
        return mode;
    }

    public StructureFilterMode getFilterMode() {
        return filterMode;
    }

    public Set<String> getAllowedWorlds() {
        return allowedWorlds;
    }

    public Set<String> getSealedWorlds() {
        return sealedWorlds;
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    public boolean isStrict() {
        return strict;
    }

    /**
     * Alias-aware decision using sealed/listed flags (computed by the listener with Multiverse aliases).
     * Fail-closed: managed + not listed → DENY; sealed always DENY for managed.
     */
    public Decision decideWithFlags(String namespace, String key, boolean sealed, boolean listed) {
        if (namespace == null || namespace.isBlank()) {
            return decide("unresolved-world", null, key);
        }
        if (!isManagedNamespace(namespace)) {
            return Decision.PASS;
        }
        if (sealed) {
            return Decision.DENY;
        }
        return switch (mode) {
            case ALLOWLIST -> {
                if (allowedWorlds.isEmpty()) {
                    yield Decision.DENY;
                }
                yield listed ? Decision.ALLOW : Decision.DENY;
            }
            case DENYLIST -> listed ? Decision.DENY : Decision.ALLOW;
        };
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
