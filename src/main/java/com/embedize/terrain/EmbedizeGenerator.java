package com.embedize.terrain;

import org.jetbrains.annotations.NotNull;

/**
 * Marker and immutable descriptor for worlds that explicitly selected an
 * Embedize generator through Bukkit or Multiverse.
 */
public interface EmbedizeGenerator {

    @NotNull TerrainGeneratorFactory.Kind kind();

    @NotNull String generatorId();
}
