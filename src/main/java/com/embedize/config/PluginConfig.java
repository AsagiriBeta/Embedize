package com.embedize.config;

import com.embedize.EmbedizePlugin;
import com.embedize.structure.IsolationPolicy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class PluginConfig {

    private final EmbedizePlugin plugin;

    private boolean enabled;
    private boolean strictIsolation;
    private boolean denyUnresolvedKeys;
    private boolean autoSealDefaultLevel;
    private IsolationPolicy.Mode mode;
    private IsolationPolicy.StructureFilterMode filterMode;
    private Set<String> allowedWorlds;
    private Set<String> sealedWorlds;
    private Set<String> managedNamespaces;
    private boolean debugCancellations;
    private boolean installTfgBridge;
    private boolean dntEnabled;
    private String dntInstallMode;
    private String dntModrinthProject;
    private boolean preferDatapackZip;
    private String pinnedVersion;
    private String installDirectory;
    private boolean resolveAliases;
    private IsolationPolicy isolationPolicy;

    public PluginConfig(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("enabled", true);
        this.strictIsolation = cfg.getBoolean("strict-isolation", true);
        this.denyUnresolvedKeys = cfg.getBoolean("deny-unresolved-structure-keys", true);
        this.autoSealDefaultLevel = cfg.getBoolean("auto-seal-default-level", true);
        this.mode = parseMode(cfg.getString("mode", "ALLOWLIST"));
        this.allowedWorlds = toLowerSet(cfg.getStringList("allowed-worlds"));
        this.sealedWorlds = toLowerSet(cfg.getStringList("sealed-worlds"));
        if (autoSealDefaultLevel) {
            this.sealedWorlds = withAutoSealed(sealedWorlds);
        }

        ConfigurationSection filter = cfg.getConfigurationSection("structure-filter");
        this.filterMode = parseFilterMode(filter == null ? cfg.getString("structure-filter-mode", "ALL_NON_MINECRAFT")
                : filter.getString("mode", "ALL_NON_MINECRAFT"));
        List<String> nsList = filter != null ? filter.getStringList("namespaces") : cfg.getStringList("managed-namespaces");
        this.managedNamespaces = toLowerSet(nsList);
        if (managedNamespaces.isEmpty() && filterMode == IsolationPolicy.StructureFilterMode.NAMESPACES) {
            // Sensible starter namespaces; operators should extend for other packs
            managedNamespaces = Set.of("nova_structures");
        }

        this.debugCancellations = cfg.getBoolean("debug-cancellations", false);

        ConfigurationSection packs = cfg.getConfigurationSection("datapacks");
        this.installTfgBridge = packs == null || packs.getBoolean("install-tfg-bridge", true);
        this.installDirectory = packs == null ? "default-world" : packs.getString("install-directory", "default-world");

        ConfigurationSection dnt = packs == null ? null : packs.getConfigurationSection("dungeons-and-taverns");
        this.dntEnabled = dnt == null || dnt.getBoolean("enabled", true);
        this.dntInstallMode = dnt == null ? "auto" : dnt.getString("install", "auto");
        this.dntModrinthProject = dnt == null ? "dungeons-and-taverns" : dnt.getString("modrinth-project", "dungeons-and-taverns");
        this.preferDatapackZip = dnt == null || dnt.getBoolean("prefer-datapack-zip", true);
        this.pinnedVersion = dnt == null ? null : dnt.getString("pinned-version", "");
        if (pinnedVersion != null && pinnedVersion.isBlank()) {
            pinnedVersion = null;
        }

        ConfigurationSection mv = cfg.getConfigurationSection("multiverse");
        this.resolveAliases = mv == null || mv.getBoolean("resolve-aliases", true);

        // Safety: never let a sealed world remain effectively allowlisted for ALLOWLIST mode messaging
        this.isolationPolicy = new IsolationPolicy(
                enabled,
                strictIsolation,
                mode,
                filterMode,
                allowedWorlds,
                sealedWorlds,
                managedNamespaces,
                denyUnresolvedKeys
        );

        if (strictIsolation && mode == IsolationPolicy.Mode.ALLOWLIST) {
            for (String sealed : sealedWorlds) {
                if (allowedWorlds.contains(sealed)) {
                    plugin.getLogger().warning("World '" + sealed + "' is in both allowed-worlds and sealed-worlds. "
                            + "Sealed wins — managed structures will NEVER generate there.");
                }
            }
        }
    }

    private Set<String> withAutoSealed(Set<String> base) {
        Set<String> out = new LinkedHashSet<>(base);
        String level = detectLevelName();
        out.add(level.toLowerCase(Locale.ROOT));
        out.add(level.toLowerCase(Locale.ROOT) + "_nether");
        out.add(level.toLowerCase(Locale.ROOT) + "_the_end");
        // Common Paper defaults even if level-name differs
        out.add("world");
        out.add("world_nether");
        out.add("world_the_end");
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL
                    && world.getName().equalsIgnoreCase(level)) {
                out.add(world.getName().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private String detectLevelName() {
        Path props = Bukkit.getWorldContainer().toPath().resolve("server.properties");
        if (Files.isRegularFile(props)) {
            try (Stream<String> lines = Files.lines(props)) {
                return lines.map(String::trim)
                        .filter(l -> l.startsWith("level-name="))
                        .map(l -> l.substring("level-name=".length()).trim())
                        .filter(s -> !s.isEmpty())
                        .findFirst()
                        .orElse("world");
            } catch (IOException ignored) {
                // fall through
            }
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().getFirst().getName();
        }
        return "world";
    }

    private static IsolationPolicy.Mode parseMode(String raw) {
        if (raw == null) {
            return IsolationPolicy.Mode.ALLOWLIST;
        }
        try {
            return IsolationPolicy.Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return IsolationPolicy.Mode.ALLOWLIST;
        }
    }

    private static IsolationPolicy.StructureFilterMode parseFilterMode(String raw) {
        if (raw == null) {
            return IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT;
        }
        try {
            return IsolationPolicy.StructureFilterMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT;
        }
    }

    private static Set<String> toLowerSet(List<String> values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                set.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public IsolationPolicy getIsolationPolicy() {
        return isolationPolicy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isStrictIsolation() {
        return strictIsolation;
    }

    public IsolationPolicy.Mode getMode() {
        return mode;
    }

    public IsolationPolicy.StructureFilterMode getFilterMode() {
        return filterMode;
    }

    public Set<String> getConfiguredWorlds() {
        return allowedWorlds;
    }

    public Set<String> getSealedWorlds() {
        return sealedWorlds;
    }

    public Set<String> getManagedNamespaces() {
        return managedNamespaces;
    }

    public boolean isDebugCancellations() {
        return debugCancellations;
    }

    public boolean isInstallTfgBridge() {
        return installTfgBridge;
    }

    public boolean isDntEnabled() {
        return dntEnabled;
    }

    public String getDntInstallMode() {
        return dntInstallMode;
    }

    public String getDntModrinthProject() {
        return dntModrinthProject;
    }

    public boolean isPreferDatapackZip() {
        return preferDatapackZip;
    }

    public String getPinnedVersion() {
        return pinnedVersion;
    }

    public String getInstallDirectory() {
        return installDirectory;
    }

    public boolean isResolveAliases() {
        return resolveAliases;
    }
}
