package com.embedize.bootstrap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Discovers plugin-owned datapacks so biomes and structures enter the vanilla registries.
 * <p>
 * Structures are assembled by Minecraft's own {@code JigsawPlacement} / structure pipeline —
 * Embedize only ships one complete datapack per upstream pack (plus a bridge pack) and enables
 * {@code shouldGenerateStructures()}.
 */
public final class EmbedizeBootstrap implements PluginBootstrap {

    public static final String BIOME_PACK_PATH = "/embedize-biomes";
    public static final String BIOME_PACK_ID = "biomes";

    public static final String STRUCTURE_PACKS_ROOT = "/embedize-structure-packs";
    public static final String STRUCTURE_PACKS_INDEX = STRUCTURE_PACKS_ROOT + "/index.json";

    /** @deprecated Prefer typed constants; kept for status/command compatibility. */
    @Deprecated
    public static final Map<String, String> BUNDLED_PACKS = Map.of(
            BIOME_PACK_ID, "Embedize custom biomes",
            "structures", "Embedize bundled structure packs"
    );

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            DatapackRegistrar registrar = event.registrar();
            discoverRequired(context, registrar, BIOME_PACK_PATH, BIOME_PACK_ID);
            discoverStructurePacks(context, registrar);
        });
        context.getLogger().info("Embedize bootstrap: biome + structure datapack discovery registered.");
    }

    private static void discoverStructurePacks(BootstrapContext context, DatapackRegistrar registrar) {
        List<String> slugs = readLoadOrder(context);
        if (slugs.isEmpty()) {
            context.getLogger().warn(
                    "Optional structure packs missing: " + STRUCTURE_PACKS_INDEX
                            + " (use fullJar / buildStructureDatapack). Vanilla structures only.");
            return;
        }
        int found = 0;
        for (String slug : slugs) {
            String path = STRUCTURE_PACKS_ROOT + "/" + slug;
            if (discoverOptional(context, registrar, path, "struct-" + slug)) {
                found++;
            }
        }
        context.getLogger().info(
                "Embedize structure packs discovered: " + found + "/" + slugs.size()
                        + " (load order from index.json)");
    }

    private static List<String> readLoadOrder(BootstrapContext context) {
        List<String> slugs = new ArrayList<>();
        try (InputStream in = EmbedizeBootstrap.class.getResourceAsStream(STRUCTURE_PACKS_INDEX)) {
            if (in == null) {
                return slugs;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray order = root.has("loadOrder") && root.get("loadOrder").isJsonArray()
                    ? root.getAsJsonArray("loadOrder")
                    : null;
            if (order == null) {
                context.getLogger().warn(STRUCTURE_PACKS_INDEX + " missing loadOrder array");
                return slugs;
            }
            for (JsonElement el : order) {
                if (el.isJsonPrimitive()) {
                    String slug = el.getAsString().trim();
                    if (!slug.isEmpty()) {
                        slugs.add(slug);
                    }
                }
            }
        } catch (Exception ex) {
            context.getLogger().warn("Unable to read " + STRUCTURE_PACKS_INDEX + ": " + ex.getMessage());
        }
        return slugs;
    }

    private static void discoverRequired(
            BootstrapContext context,
            DatapackRegistrar registrar,
            String resourcePath,
            String id
    ) {
        URL resource = EmbedizeBootstrap.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing " + resourcePath + " in plugin JAR");
        }
        try {
            URI uri = resource.toURI();
            var discovered = registrar.discoverPack(uri, id);
            if (discovered == null) {
                throw new IllegalStateException("Datapack registrar returned null for " + id);
            }
            context.getLogger().info("Discovered Embedize datapack: " + discovered.getName());
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Unable to discover Embedize datapack " + id, e);
        }
    }

    /**
     * @return true if the pack was discovered successfully
     */
    private static boolean discoverOptional(
            BootstrapContext context,
            DatapackRegistrar registrar,
            String resourcePath,
            String id
    ) {
        URL resource = EmbedizeBootstrap.class.getResource(resourcePath);
        if (resource == null) {
            context.getLogger().warn("Optional datapack missing: " + resourcePath);
            return false;
        }
        try {
            URI uri = resource.toURI();
            var discovered = registrar.discoverPack(uri, id);
            if (discovered == null) {
                context.getLogger().warn("Datapack registrar returned null for optional pack " + id);
                return false;
            }
            context.getLogger().info("Discovered Embedize datapack: " + discovered.getName());
            return true;
        } catch (URISyntaxException | IOException e) {
            context.getLogger().warn("Unable to discover optional datapack " + id + ": " + e.getMessage());
            return false;
        }
    }
}
