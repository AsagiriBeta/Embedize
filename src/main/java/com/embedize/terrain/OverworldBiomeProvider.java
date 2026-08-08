package com.embedize.terrain;

import com.embedize.terrain.biome.EmbedizeBiomes;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Climate → biome map using vanilla biomes as the canvas.
 * <p>
 * The only custom biome injected here is {@code embedize:abyssal_deep} for far-ocean trenches
 * (a role ordinary {@code deep_ocean} does not express). Terrain/decoration differences for
 * plains/forest/etc. are applied in Java generators, not via rename overlays.
 * <p>
 * <b>3D biomes:</b> {@link #getBiome} is Y-aware. Below the surface column, climate selects
 * {@code lush_caves} / {@code dripstone_caves}; sparse deep continental pockets become
 * {@code deep_dark} (for ancient cities). Surface painting via {@link #biomeAt} stays 2D.
 */
public final class OverworldBiomeProvider extends BiomeProvider {

    /**
     * Natural overworld vanilla biome ids (surface + caves + oceans/rivers).
     * Keys only — avoids touching Paper {@link Biome} during unit-test class init.
     */
    static final List<String> VANILLA_OVERWORLD_BIOME_KEYS = List.of(
            // Oceans
            "minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
            "minecraft:cold_ocean", "minecraft:frozen_ocean",
            "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:deep_frozen_ocean",
            // Shores / rivers
            "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore",
            "minecraft:river", "minecraft:frozen_river",
            // Temperate
            "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
            "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
            "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:cherry_grove",
            // Cold
            "minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
            "minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:ice_spikes",
            // Warm
            "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
            "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna",
            "minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands",
            "minecraft:swamp", "minecraft:mangrove_swamp",
            // Rare island
            "minecraft:mushroom_fields",
            // Hills / alpine
            "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills",
            "minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks",
            "minecraft:grove", "minecraft:snowy_slopes",
            // Caves (Y-layered)
            "minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark"
    );

    private final OverworldNoiseModel model;
    private List<Biome> cachedBiomes;

    public OverworldBiomeProvider(long seed) {
        this.model = new OverworldNoiseModel(seed);
    }

    OverworldBiomeProvider(OverworldNoiseModel model) {
        this.model = model;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return toBiome(pickKey3d(x, y, z, worldInfo.getMinHeight()));
    }

    @NotNull Biome biomeFromSample(OverworldNoiseModel.Sample local) {
        return toBiome(classifySurfaceKey(local, local, local.surfaceY(), 0.0));
    }

    /** Same as {@link #biomeFromSample} with an explicitly supplied surface height. */
    @NotNull Biome biomeAt(OverworldNoiseModel.Sample local, int surfaceY) {
        return toBiome(classifySurfaceKey(local, local, surfaceY, 0.0));
    }

    /** Package-visible 3D key pick for unit tests (overworld minY default −64). */
    @NotNull String pickKey3d(int x, int y, int z) {
        return pickKey3d(x, y, z, -64);
    }

    private String pickKey3d(int x, int y, int z, int minY) {
        OverworldNoiseModel.Sample local = model.sample(x, z);
        double weird = model.weirdnessAt(x, z);
        String cave = undergroundBiomeKey(x, y, z, local, local.surfaceY(), minY);
        if (cave != null) {
            return cave;
        }
        return classifySurfaceKey(local, local, local.surfaceY(), weird);
    }

    /**
     * Cave / deep-dark layer. Returns null to keep the surface climate biome
     * (near surface, ocean water columns, or non-matching depths).
     */
    private @Nullable String undergroundBiomeKey(
            int x,
            int y,
            int z,
            OverworldNoiseModel.Sample local,
            int surface,
            int minY
    ) {
        // Stay surface biome in the top of the column and in open ocean water.
        if (y >= surface - 6) {
            return null;
        }
        if (OverworldNoiseModel.isTrueOcean(local) && y >= Math.min(surface + 2, OverworldNoiseModel.SEA_LEVEL - 8)) {
            return null;
        }
        if (y <= minY + 4) {
            return null;
        }

        // deep_dark: sparse continental pockets well below sea / deep underground.
        // Include Y==0 so `/locate biome` from sea-level origins still samples hits
        // (ancient cities sit ~Y -51; structure filter uses the same BiomeProvider).
        if (y <= 0
                && local.land() > 0.55
                && local.continentalness() > -0.05
                && !local.river()
                && !OverworldNoiseModel.isTrueOcean(local)) {
            double affinity = model.deepDarkAffinity(x, z);
            // Sparse but locate-friendly: ~few-km pockets under continental land.
            // Deeper Y slightly widens the domain (ancient_city sits near Y≈-50).
            double depthBoost = y < -32 ? 0.08 : (y < -8 ? 0.04 : 0.0);
            if (affinity > 0.48 - depthBoost) {
                return "minecraft:deep_dark";
            }
        }

        // Cave climate band: below surface and mostly below mid-altitude.
        int caveCeiling = Math.min(surface - 8, 48);
        if (y > caveCeiling || y < minY + 5) {
            return null;
        }
        // Skip carving cave biomes under deep ocean floors (keep ocean biomes).
        if (OverworldNoiseModel.isTrueOcean(local) && surface < OverworldNoiseModel.SEA_LEVEL - 18) {
            return null;
        }

        double h = local.humidity();
        double c = local.continentalness();
        // Humid → lush_caves; dry / inland continental → dripstone_caves.
        if (h > 0.12) {
            return "minecraft:lush_caves";
        }
        if (h < -0.05 || c > 0.05) {
            return "minecraft:dripstone_caves";
        }
        return h >= 0.0 ? "minecraft:lush_caves" : "minecraft:dripstone_caves";
    }

    /**
     * @param local column land/river for placement gates
     * @param climate temperature/humidity/continental domain
     * @param surface column surface height used for water vs land
     * @param weird climate weirdness for rare surface variants
     */
    static String classifySurfaceKey(
            OverworldNoiseModel.Sample local,
            OverworldNoiseModel.Sample climate,
            int surface,
            double weird
    ) {
        double t = climate.temperature();
        double h = climate.humidity();
        double land = local.land();
        int sea = OverworldNoiseModel.SEA_LEVEL;

        if (local.river() && surface <= sea + 2) {
            return t < -0.35 ? "minecraft:frozen_river" : "minecraft:river";
        }

        // Unique custom slot: far-ocean trench (not ordinary deep_ocean).
        if (local.oceanTrench() && surface < sea - 20 && !local.river() && land < 0.18 && t < 0.5) {
            return "embedize:abyssal_deep";
        }

        // Rare mushroom island: small emergent patch in ocean with high weirdness.
        if (!local.river()
                && land >= 0.44 && land < 0.52
                && climate.continentalness() < -0.18
                && climate.continentalness() > -0.42
                && Math.abs(weird) > 0.72
                && surface >= sea && surface <= sea + 6
                && local.mountainWeight() < 0.15) {
            return "minecraft:mushroom_fields";
        }

        // Water below sea level is NOT automatically ocean. Tiny inland ponds were being
        // labeled lukewarm_ocean — those are lakes (land biome + water column).
        if (surface < sea) {
            boolean trueOcean = !local.river()
                    && land < 0.46
                    && climate.continentalness() < -0.20;
            if (!trueOcean) {
                if (h > 0.35) {
                    return t < -0.2 ? "minecraft:swamp" : "minecraft:mangrove_swamp";
                }
                if (t < -0.35) {
                    return "minecraft:frozen_river";
                }
                // Inland lake / flooded lowland.
                return h > 0.1 ? "minecraft:forest" : "minecraft:plains";
            }
            boolean deep = surface < sea - 12
                    && land < 0.34
                    && climate.continentalness() < -0.28;
            if (deep) {
                if (t < -0.4) {
                    return "minecraft:deep_frozen_ocean";
                }
                if (t > 0.35) {
                    return "minecraft:deep_lukewarm_ocean";
                }
                if (t < -0.12) {
                    return "minecraft:deep_cold_ocean";
                }
                return "minecraft:deep_ocean";
            }
            if (t < -0.4) {
                return "minecraft:frozen_ocean";
            }
            if (t > 0.4) {
                return "minecraft:warm_ocean";
            }
            if (t > 0.1) {
                return "minecraft:lukewarm_ocean";
            }
            if (t < -0.15) {
                return "minecraft:cold_ocean";
            }
            return "minecraft:ocean";
        }

        // Beach = short ocean↔land ribbon only (not a sand continent).
        // Gate with continentalness so mid-continent flats near sea height stay grass/forest.
        double c = climate.continentalness();
        boolean shoreRibbon = !local.river()
                && surface >= sea
                && surface <= sea + 1
                && land >= 0.46
                && land < 0.49
                && c >= -0.36
                && c < -0.22;
        if (shoreRibbon) {
            if (t < -0.35) {
                return "minecraft:snowy_beach";
            }
            if (Math.abs(local.erosion()) > 0.42 || (local.erosion() < -0.15 && t > 0.1)) {
                return "minecraft:stony_shore";
            }
            return "minecraft:beach";
        }

        double peakW = local.peakWeight();
        double mountW = local.mountainWeight();
        // Swamps are low, flat basins only — never paint mangrove on hillside cliffs.
        boolean swampBasin = surface <= sea + 4
                && mountW < 0.20
                && peakW < 0.10
                && land >= 0.48;

        // Near-coast hinterland stays normal biomes (grass/forest), not sand.
        if (land < 0.58 && surface <= sea + 8 && mountW < 0.22) {
            if (h > 0.40 && swampBasin) {
                return "minecraft:mangrove_swamp";
            }
            if (t < -0.25) {
                return "minecraft:snowy_plains";
            }
            if (Math.abs(local.erosion()) > 0.4) {
                return "minecraft:windswept_hills";
            }
            return h > 0.15 ? "minecraft:forest" : "minecraft:plains";
        }

        // Alpine ladder uses continuous weights + height so foothills paint slopes,
        // not a single cliff column flipping to jagged_peaks.
        if (peakW > 0.55 || (peakW > 0.35 && surface >= 190) || surface >= 220) {
            if (t < -0.25) {
                return "minecraft:frozen_peaks";
            }
            if (h < -0.2) {
                return "minecraft:stony_peaks";
            }
            return "minecraft:jagged_peaks";
        }
        if (mountW > 0.28 || peakW > 0.18 || surface >= 120) {
            if (t < -0.2) {
                return h > 0 ? "minecraft:grove" : "minecraft:snowy_slopes";
            }
            if (climate.erosion() < -0.2 && mountW > 0.45) {
                return "minecraft:windswept_gravelly_hills";
            }
            // Lower foothills stay forested hills; higher get windswept.
            if (surface < 135 && mountW < 0.50 && peakW < 0.25) {
                return h > 0.1 ? "minecraft:forest" : "minecraft:windswept_hills";
            }
            return h > 0.1 ? "minecraft:windswept_forest" : "minecraft:windswept_hills";
        }

        if (t < -0.4) {
            if (h > 0.2) {
                return "minecraft:snowy_taiga";
            }
            if (h < -0.35 && Math.abs(climate.ridges()) > 0.55) {
                return "minecraft:ice_spikes";
            }
            return "minecraft:snowy_plains";
        }

        if (t > 0.45) {
            if (h < -0.25) {
                if (climate.erosion() < -0.35 && Math.abs(weird) > 0.35) {
                    return "minecraft:eroded_badlands";
                }
                if (h > -0.40 && climate.erosion() < -0.15) {
                    return "minecraft:wooded_badlands";
                }
                return climate.erosion() < -0.2 ? "minecraft:badlands" : "minecraft:desert";
            }
            if (h < 0.05) {
                if (Math.abs(local.erosion()) > 0.45 || Math.abs(weird) > 0.55) {
                    return "minecraft:windswept_savanna";
                }
                return surface > 90 ? "minecraft:savanna_plateau" : "minecraft:savanna";
            }
            // Warm + wet at elevation is jungle, not a cliff-top mangrove.
            if (h > 0.35) {
                if (swampBasin) {
                    return "minecraft:mangrove_swamp";
                }
                return h > 0.55 ? "minecraft:bamboo_jungle" : "minecraft:jungle";
            }
            return "minecraft:sparse_jungle";
        }

        if (h > 0.42) {
            if (swampBasin) {
                return t > 0.1 ? "minecraft:mangrove_swamp" : "minecraft:swamp";
            }
            // Elevated humidity → lush woods, never hillside swamp.
            return t > 0.1 ? "minecraft:jungle" : "minecraft:dark_forest";
        }
        if (h > 0.2) {
            if (Math.abs(climate.ridges()) > 0.55) {
                return "minecraft:cherry_grove";
            }
            if (h > 0.35) {
                return "minecraft:dark_forest";
            }
            if (Math.abs(climate.continentalness()) > 0.25) {
                return "minecraft:flower_forest";
            }
            // Old-growth birch when weirdness spikes in birch climate.
            if (t < 0.2 && Math.abs(weird) > 0.48) {
                return "minecraft:old_growth_birch_forest";
            }
            return "minecraft:forest";
        }
        if (h > 0.0) {
            if (t < -0.05) {
                // Cool humid taiga → old-growth spruce/pine variants.
                if (Math.abs(weird) > 0.50 && h > 0.12) {
                    return weird > 0
                            ? "minecraft:old_growth_spruce_taiga"
                            : "minecraft:old_growth_pine_taiga";
                }
                return "minecraft:taiga";
            }
            if (Math.abs(weird) > 0.52) {
                return "minecraft:old_growth_birch_forest";
            }
            return "minecraft:birch_forest";
        }
        if (h > -0.25) {
            if (Math.abs(climate.ridges()) > 0.45 || (t > 0.15 && h > -0.1 && h < 0.15)) {
                return "minecraft:sunflower_plains";
            }
            return "minecraft:plains";
        }
        if (t < 0.15 && h < -0.25) {
            return "minecraft:meadow";
        }
        if (surface > 95 && h < -0.15 && t < 0.25) {
            return "minecraft:meadow";
        }
        return "minecraft:plains";
    }

    private static @NotNull Biome toBiome(@NotNull String key) {
        if (key.startsWith("embedize:")) {
            Biome custom = EmbedizeBiomes.resolve(key);
            return custom != null ? custom : Biome.DEEP_OCEAN;
        }
        return switch (key) {
            case "minecraft:ocean" -> Biome.OCEAN;
            case "minecraft:deep_ocean" -> Biome.DEEP_OCEAN;
            case "minecraft:warm_ocean" -> Biome.WARM_OCEAN;
            case "minecraft:lukewarm_ocean" -> Biome.LUKEWARM_OCEAN;
            case "minecraft:cold_ocean" -> Biome.COLD_OCEAN;
            case "minecraft:frozen_ocean" -> Biome.FROZEN_OCEAN;
            case "minecraft:deep_cold_ocean" -> Biome.DEEP_COLD_OCEAN;
            case "minecraft:deep_lukewarm_ocean" -> Biome.DEEP_LUKEWARM_OCEAN;
            case "minecraft:deep_frozen_ocean" -> Biome.DEEP_FROZEN_OCEAN;
            case "minecraft:beach" -> Biome.BEACH;
            case "minecraft:snowy_beach" -> Biome.SNOWY_BEACH;
            case "minecraft:stony_shore" -> Biome.STONY_SHORE;
            case "minecraft:river" -> Biome.RIVER;
            case "minecraft:frozen_river" -> Biome.FROZEN_RIVER;
            case "minecraft:plains" -> Biome.PLAINS;
            case "minecraft:sunflower_plains" -> Biome.SUNFLOWER_PLAINS;
            case "minecraft:meadow" -> Biome.MEADOW;
            case "minecraft:forest" -> Biome.FOREST;
            case "minecraft:flower_forest" -> Biome.FLOWER_FOREST;
            case "minecraft:birch_forest" -> Biome.BIRCH_FOREST;
            case "minecraft:old_growth_birch_forest" -> Biome.OLD_GROWTH_BIRCH_FOREST;
            case "minecraft:dark_forest" -> Biome.DARK_FOREST;
            case "minecraft:cherry_grove" -> Biome.CHERRY_GROVE;
            case "minecraft:taiga" -> Biome.TAIGA;
            case "minecraft:old_growth_pine_taiga" -> Biome.OLD_GROWTH_PINE_TAIGA;
            case "minecraft:old_growth_spruce_taiga" -> Biome.OLD_GROWTH_SPRUCE_TAIGA;
            case "minecraft:snowy_taiga" -> Biome.SNOWY_TAIGA;
            case "minecraft:snowy_plains" -> Biome.SNOWY_PLAINS;
            case "minecraft:ice_spikes" -> Biome.ICE_SPIKES;
            case "minecraft:jungle" -> Biome.JUNGLE;
            case "minecraft:sparse_jungle" -> Biome.SPARSE_JUNGLE;
            case "minecraft:bamboo_jungle" -> Biome.BAMBOO_JUNGLE;
            case "minecraft:savanna" -> Biome.SAVANNA;
            case "minecraft:savanna_plateau" -> Biome.SAVANNA_PLATEAU;
            case "minecraft:windswept_savanna" -> Biome.WINDSWEPT_SAVANNA;
            case "minecraft:desert" -> Biome.DESERT;
            case "minecraft:badlands" -> Biome.BADLANDS;
            case "minecraft:eroded_badlands" -> Biome.ERODED_BADLANDS;
            case "minecraft:wooded_badlands" -> Biome.WOODED_BADLANDS;
            case "minecraft:swamp" -> Biome.SWAMP;
            case "minecraft:mangrove_swamp" -> Biome.MANGROVE_SWAMP;
            case "minecraft:mushroom_fields" -> Biome.MUSHROOM_FIELDS;
            case "minecraft:windswept_hills" -> Biome.WINDSWEPT_HILLS;
            case "minecraft:windswept_forest" -> Biome.WINDSWEPT_FOREST;
            case "minecraft:windswept_gravelly_hills" -> Biome.WINDSWEPT_GRAVELLY_HILLS;
            case "minecraft:jagged_peaks" -> Biome.JAGGED_PEAKS;
            case "minecraft:frozen_peaks" -> Biome.FROZEN_PEAKS;
            case "minecraft:stony_peaks" -> Biome.STONY_PEAKS;
            case "minecraft:grove" -> Biome.GROVE;
            case "minecraft:snowy_slopes" -> Biome.SNOWY_SLOPES;
            case "minecraft:lush_caves" -> Biome.LUSH_CAVES;
            case "minecraft:dripstone_caves" -> Biome.DRIPSTONE_CAVES;
            case "minecraft:deep_dark" -> Biome.DEEP_DARK;
            default -> Biome.PLAINS;
        };
    }

    /** Catalog used by {@link #getBiomes} (resolves Bukkit biome handles — server only). */
    @NotNull List<Biome> biomeCatalog() {
        if (cachedBiomes == null) {
            List<Biome> list = new ArrayList<>();
            list.addAll(EmbedizeBiomes.resolveAllCustom(null));
            for (String key : VANILLA_OVERWORLD_BIOME_KEYS) {
                list.add(toBiome(key));
            }
            cachedBiomes = List.copyOf(list.stream().distinct().toList());
        }
        return cachedBiomes;
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return biomeCatalog();
    }
}
