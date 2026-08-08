package com.embedize.terrain;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Nether climate patches on vanilla biomes (Incendium multi-noise spirit via Java terrain).
 */
public final class NetherBiomeProvider extends BiomeProvider {

    private final NetherNoiseModel model;
    private List<Biome> cachedBiomes;

    public NetherBiomeProvider(long seed) {
        this.model = new NetherNoiseModel(seed);
    }

    NetherBiomeProvider(NetherNoiseModel model) {
        this.model = model;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        NetherNoiseModel.Sample s = model.sample(x, z);
        double a = s.climateA();
        double b = s.climateB();
        double c = s.climateC();

        if (c < -0.45 && a < 0.1) {
            return Biome.SOUL_SAND_VALLEY;
        }
        if (s.volcanic() || (a < -0.2 && b > 0.35)) {
            return Biome.BASALT_DELTAS;
        }
        if (a < -0.4) {
            return Biome.SOUL_SAND_VALLEY;
        }
        if (a > 0.25 && b > 0.20) {
            return Biome.CRIMSON_FOREST;
        }
        if (a > 0.25) {
            return Biome.WARPED_FOREST;
        }
        if (b > 0.55 || (s.pillar() && c > 0.1)) {
            return Biome.BASALT_DELTAS;
        }
        return Biome.NETHER_WASTES;
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        if (cachedBiomes == null) {
            cachedBiomes = List.of(
                    Biome.NETHER_WASTES,
                    Biome.SOUL_SAND_VALLEY,
                    Biome.CRIMSON_FOREST,
                    Biome.WARPED_FOREST,
                    Biome.BASALT_DELTAS
            );
        }
        return cachedBiomes;
    }
}
