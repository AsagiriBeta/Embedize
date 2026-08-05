package com.embedize.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * A named datapack structure group with its own world whitelist.
 * {@code packs} are user-facing datapack ids; {@code namespaces} drive isolation.
 */
public final class StructureGroup {

    private final String id;
    private String displayName;
    private final Set<String> packs;
    private final Set<String> namespaces;
    private final Set<String> allowedWorlds;

    public StructureGroup(
            String id,
            String displayName,
            Collection<String> packs,
            Collection<String> namespaces,
            Collection<String> allowedWorlds
    ) {
        this.id = normalizeId(id);
        if (this.id == null) {
            throw new IllegalArgumentException("Invalid group id");
        }
        this.displayName = displayName == null || displayName.isBlank() ? this.id : displayName.trim();
        this.packs = new LinkedHashSet<>(normalizeAll(packs));
        this.namespaces = new LinkedHashSet<>(normalizeAll(namespaces));
        this.namespaces.remove("minecraft");
        this.allowedWorlds = new LinkedHashSet<>(normalizeAll(allowedWorlds));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName.trim();
        }
    }

    public Set<String> getPacks() {
        return Collections.unmodifiableSet(packs);
    }

    public Set<String> getNamespaces() {
        return Collections.unmodifiableSet(namespaces);
    }

    public Set<String> getAllowedWorlds() {
        return Collections.unmodifiableSet(allowedWorlds);
    }

    public boolean addPack(String packId) {
        String p = normalizeOne(packId);
        return p != null && packs.add(p);
    }

    public boolean removePack(String packId) {
        String p = normalizeOne(packId);
        return p != null && packs.remove(p);
    }

    public boolean hasPack(String packId) {
        String p = normalizeOne(packId);
        return p != null && packs.contains(p);
    }

    public boolean addNamespace(String namespace) {
        String ns = normalizeOne(namespace);
        if (ns == null || "minecraft".equals(ns)) {
            return false;
        }
        return namespaces.add(ns);
    }

    public boolean removeNamespace(String namespace) {
        String ns = normalizeOne(namespace);
        return ns != null && namespaces.remove(ns);
    }

    public boolean addWorld(String world) {
        String w = normalizeOne(world);
        return w != null && allowedWorlds.add(w);
    }

    public boolean removeWorld(String world) {
        String w = normalizeOne(world);
        return w != null && allowedWorlds.remove(w);
    }

    public boolean allowsWorld(String worldName) {
        String w = normalizeOne(worldName);
        return w != null && allowedWorlds.contains(w);
    }

    public boolean ownsNamespace(String namespace) {
        String ns = normalizeOne(namespace);
        return ns != null && namespaces.contains(ns);
    }

    public List<String> packList() {
        return new ArrayList<>(packs);
    }

    public List<String> namespaceList() {
        return new ArrayList<>(namespaces);
    }

    public List<String> worldList() {
        return new ArrayList<>(allowedWorlds);
    }

    public static String normalizeId(String id) {
        String n = normalizeOne(id);
        if (n == null) {
            return null;
        }
        if (!n.matches("[a-z0-9][a-z0-9_\\-]*")) {
            return null;
        }
        return n;
    }

    private static String normalizeOne(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeAll(Collection<String> values) {
        Set<String> out = new LinkedHashSet<>();
        if (values == null) {
            return out;
        }
        for (String v : values) {
            String n = normalizeOne(v);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StructureGroup that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
