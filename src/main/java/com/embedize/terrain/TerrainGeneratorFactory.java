package com.embedize.terrain;

import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Multiverse generator ids:
 * <ul>
 *   <li>{@code Embedize} / {@code Embedize:overworld} — normal</li>
 *   <li>{@code Embedize:nether} / {@code Embedize:Embedize-Nether} — nether</li>
 *   <li>{@code Embedize:end} / {@code Embedize:Embedize-End} — the end (Nullscape-inspired)</li>
 * </ul>
 */
public final class TerrainGeneratorFactory {

    private TerrainGeneratorFactory() {
    }

    public enum Kind {
        OVERWORLD,
        NETHER,
        END
    }

    public static @NotNull Kind resolveKind(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return Kind.OVERWORLD;
        }
        String key = id.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '-')
                .replace('_', '-');
        if (key.contains("nether") || key.equals("hell") || key.equals("inc")) {
            return Kind.NETHER;
        }
        if (key.contains("end") || key.contains("nullscape")) {
            return Kind.END;
        }
        return switch (key) {
            case "overworld", "ow", "normal", "default", "embedize", "terrain", "noise" -> Kind.OVERWORLD;
            default -> Kind.OVERWORLD;
        };
    }

    public static @NotNull ChunkGenerator create(@NotNull String worldName, @Nullable String id) {
        return switch (resolveKind(id)) {
            case NETHER -> new NetherTerrainGenerator(worldName, id);
            case END -> new EndTerrainGenerator(worldName, id);
            case OVERWORLD -> new OverworldTerrainGenerator(worldName, id);
        };
    }

    public static @NotNull String describe(@Nullable String id) {
        return switch (resolveKind(id)) {
            case NETHER -> "Embedize-Nether";
            case END -> "Embedize-End";
            case OVERWORLD -> "Embedize (overworld)";
        };
    }
}
