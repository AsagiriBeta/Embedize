package com.embedize.terrain.biome;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Catalog of Embedize custom biomes that are <em>genuinely distinct</em> from vanilla.
 * <p>
 * Philosophy: keep {@code minecraft:*} biomes as the canvas; shape terrain/decoration in Java.
 * Only register a custom biome when it fills a role vanilla does not have
 * (e.g. far-ocean trench {@code abyssal_deep}). Never 1:1-rename plains/forest/etc.
 */
public final class EmbedizeBiomes {

    public static final String NAMESPACE = "embedize";

    /** Custom id → vanilla structure-filter equivalent (for biome tags / StructureSets). */
    private static final Map<String, String> CUSTOM_TO_VANILLA = new LinkedHashMap<>();

    static {
        // Far-ocean trench / 渊海陷窟 spirit — not a rename of ordinary deep_ocean columns.
        map("abyssal_deep", "minecraft:deep_ocean");
    }

    private static volatile boolean loggedMissing;

    private EmbedizeBiomes() {
    }

    private static void map(String id, String vanilla) {
        CUSTOM_TO_VANILLA.put(NAMESPACE + ":" + id, vanilla);
    }

    public static @NotNull Map<String, String> customToVanilla() {
        return Collections.unmodifiableMap(CUSTOM_TO_VANILLA);
    }

    public static int catalogSize() {
        return CUSTOM_TO_VANILLA.size();
    }

    public static @NotNull String keyOf(@NotNull Biome biome) {
        try {
            NamespacedKey nk = biome.getKey();
            return nk.getNamespace() + ":" + nk.getKey();
        } catch (Throwable ex) {
            return "minecraft:" + biome.toString().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Vanilla biome id used for structure allow-lists (custom biomes resolve to their base).
     */
    public static @NotNull String vanillaEquivalentKey(@NotNull Biome biome) {
        String key = keyOf(biome).toLowerCase(Locale.ROOT);
        return CUSTOM_TO_VANILLA.getOrDefault(key, key);
    }

    public static boolean isCustom(@NotNull Biome biome) {
        return keyOf(biome).toLowerCase(Locale.ROOT).startsWith(NAMESPACE + ":");
    }

    public static boolean isLike(@NotNull Biome biome, @NotNull Biome vanillaLike) {
        if (biome == vanillaLike) {
            return true;
        }
        String left = vanillaEquivalentKey(biome);
        String right = vanillaEquivalentKey(vanillaLike);
        return left.equalsIgnoreCase(right);
    }

    public static boolean isLikeAny(@NotNull Biome biome, @NotNull Biome... vanillaLikes) {
        for (Biome v : vanillaLikes) {
            if (isLike(biome, v)) {
                return true;
            }
        }
        return false;
    }

    /** Match {@code embedize:<id>} or bare id. */
    public static boolean isId(@NotNull Biome biome, @NotNull String id) {
        String key = keyOf(biome).toLowerCase(Locale.ROOT);
        String needle = id.toLowerCase(Locale.ROOT);
        return key.equals(needle) || key.equals(NAMESPACE + ":" + needle) || key.endsWith(":" + needle);
    }

    public static boolean isAbyssalDeep(@NotNull Biome biome) {
        return isId(biome, "abyssal_deep");
    }

    /**
     * @deprecated Vanilla biomes are the canvas; do not remap climate slots to custom renames.
     *             Returns {@code vanillaFallback} unchanged.
     */
    @Deprecated
    public static @NotNull Biome ofVanillaRole(@NotNull Biome vanillaFallback) {
        return vanillaFallback;
    }

    public static @Nullable Biome resolve(@NotNull String namespacedKey) {
        String key = namespacedKey.toLowerCase(Locale.ROOT);
        try {
            Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
            Biome fromPaper = registry.get(Key.key(key));
            if (fromPaper != null) {
                return fromPaper;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            NamespacedKey nk = NamespacedKey.fromString(key);
            if (nk != null) {
                return Registry.BIOME.get(nk);
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return null;
    }

    public static @NotNull List<Biome> resolveAllCustom(@Nullable Logger logger) {
        List<Biome> out = new ArrayList<>(CUSTOM_TO_VANILLA.size());
        List<String> missing = new ArrayList<>();
        for (String id : CUSTOM_TO_VANILLA.keySet()) {
            Biome b = resolve(id);
            if (b != null) {
                out.add(b);
            } else {
                missing.add(id);
            }
        }
        if (!missing.isEmpty() && logger != null && !loggedMissing) {
            loggedMissing = true;
            logger.warning("Embedize custom biomes not in registry yet (will use vanilla fallbacks): "
                    + missing.size() + "/" + CUSTOM_TO_VANILLA.size()
                    + " missing e.g. " + missing.get(0));
        }
        return List.copyOf(out);
    }
}
