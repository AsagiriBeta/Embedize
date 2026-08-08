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
import java.util.function.Consumer;

/**
 * Discovers plugin-owned datapacks so biomes and structures can enter the vanilla registries.
 * <p>
 * <strong>Product intent (TFG-aligned isolation):</strong> bundled structures should only
 * naturally generate in worlds that use an Embedize generator. TerraformGenerator achieves
 * this by hanging structures on its NMS {@code ChunkGenerator} instance — non-TFG worlds
 * never see those definitions. Embedize keeps real DnT/etc. datapacks for the vanilla
 * jigsaw engine, so packs must still be <em>discovered</em> here.
 * <p>
 * Paper/Leaves have <em>no</em> per-world datapack/registry API ({@code DATAPACK_DISCOVERY}
 * is server-global). Packs are discovered with {@code autoEnableOnServerStart(false)};
 * {@code BundledDatapackSync} enables them only when an Embedize world exists or is
 * configured. {@code StructureWorldGateListener} remains the spawn/locate firewall while
 * packs are enabled (shared registry).
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
            // Do not force-enable on every start: BundledDatapackSync turns packs on
            // only when Embedize worlds need them (closer to TFG "follow the generator").
            Consumer<DatapackRegistrar.Configurer> lazy =
                    c -> c.autoEnableOnServerStart(false);
            discoverRequired(context, registrar, BIOME_PACK_PATH, BIOME_PACK_ID, lazy);
            discoverStructurePacks(context, registrar, lazy);
        });
        context.getLogger().info(
                "Embedize bootstrap: biome + structure datapack discovery registered "
                        + "(autoEnableOnServerStart=false; runtime sync enables for Embedize worlds).");
    }

    private static void discoverStructurePacks(
            BootstrapContext context,
            DatapackRegistrar registrar,
            Consumer<DatapackRegistrar.Configurer> configurer
    ) {
        List<String> slugs = readLoadOrder(context);
        if (slugs.isEmpty()) {
            context.getLogger().warn(
                    "Optional structure packs missing: " + STRUCTURE_PACKS_INDEX
                            + " (use jar / buildStructureDatapack). Vanilla structures only.");
            return;
        }
        int found = 0;
        for (String slug : slugs) {
            String path = STRUCTURE_PACKS_ROOT + "/" + slug;
            if (discoverOptional(context, registrar, path, "struct-" + slug, configurer)) {
                found++;
            }
        }
        context.getLogger().info(
                "Embedize structure packs discovered: " + found + "/" + slugs.size()
                        + " (load order from index.json; enable deferred to BundledDatapackSync)");
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
            String id,
            Consumer<DatapackRegistrar.Configurer> configurer
    ) {
        URL resource = EmbedizeBootstrap.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing " + resourcePath + " in plugin JAR");
        }
        try {
            URI uri = resource.toURI();
            var discovered = registrar.discoverPack(uri, id, configurer);
            if (discovered == null) {
                throw new IllegalStateException("Datapack registrar returned null for " + id);
            }
            context.getLogger().info("Discovered Embedize datapack: " + discovered.getName()
                    + " (lazy enable)");
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
            String id,
            Consumer<DatapackRegistrar.Configurer> configurer
    ) {
        URL resource = EmbedizeBootstrap.class.getResource(resourcePath);
        if (resource == null) {
            context.getLogger().warn("Optional datapack missing: " + resourcePath);
            return false;
        }
        try {
            URI uri = resource.toURI();
            var discovered = registrar.discoverPack(uri, id, configurer);
            if (discovered == null) {
                context.getLogger().warn("Datapack registrar returned null for optional pack " + id);
                return false;
            }
            context.getLogger().info("Discovered Embedize datapack: " + discovered.getName()
                    + " (lazy enable)");
            return true;
        } catch (URISyntaxException | IOException e) {
            context.getLogger().warn("Unable to discover optional datapack " + id + ": " + e.getMessage());
            return false;
        }
    }
}
