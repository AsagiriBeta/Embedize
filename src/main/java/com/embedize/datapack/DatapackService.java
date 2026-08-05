package com.embedize.datapack;

import com.embedize.EmbedizePlugin;
import com.embedize.config.PluginConfig;
import com.embedize.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Optional helper: installs the plugin-bundled TerraformGenerator biome-tag bridge.
 * Structure datapacks themselves are not downloaded — place them in the world datapacks folder.
 */
public final class DatapackService {

    public static final String TFG_BRIDGE_FOLDER = "embedize-tfg-biome-bridge";

    private final EmbedizePlugin plugin;
    private final PluginConfig config;
    private final AtomicBoolean restartHintPrinted = new AtomicBoolean(false);

    public DatapackService(EmbedizePlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void ensureInstalled() throws IOException {
        if (!config.isInstallTfgBridge()) {
            return;
        }
        Path datapacksDir = resolveDatapacksDirectory();
        Files.createDirectories(datapacksDir);
        if (installTfgBridge(datapacksDir)) {
            maybeHintReload();
        }
    }

    public Path resolveDatapacksDirectory() throws IOException {
        String configured = config.getInstallDirectory();
        if (configured != null && !configured.isBlank() && !"default-world".equalsIgnoreCase(configured)) {
            Path path = Path.of(configured);
            if (!path.isAbsolute()) {
                path = plugin.getDataFolder().toPath().resolve(path);
            }
            return path;
        }

        World primary = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (primary != null) {
            return primary.getWorldFolder().toPath().resolve("datapacks");
        }

        Path container = Bukkit.getWorldContainer().toPath();
        Path levelName = container.resolve("world").resolve("datapacks");
        Path props = container.resolve("server.properties");
        if (Files.isRegularFile(props)) {
            try (Stream<String> lines = Files.lines(props)) {
                String name = lines
                        .map(String::trim)
                        .filter(l -> l.startsWith("level-name="))
                        .map(l -> l.substring("level-name=".length()).trim())
                        .findFirst()
                        .orElse("world");
                return container.resolve(name).resolve("datapacks");
            }
        }
        return levelName;
    }

    private boolean installTfgBridge(Path datapacksDir) throws IOException {
        Path target = datapacksDir.resolve(TFG_BRIDGE_FOLDER);
        Path marker = target.resolve(".embedize-installed");
        String resourceRoot = "datapacks/tfg-biome-bridge/";

        String pluginVersion = plugin.getDescription().getVersion();
        if (Files.isDirectory(target) && Files.isRegularFile(marker)) {
            String installed = Files.readString(marker).trim();
            if (pluginVersion.equals(installed)) {
                return false;
            }
        }

        if (Files.exists(target)) {
            deleteRecursive(target);
        }
        Files.createDirectories(target);
        copyResourceTree(resourceRoot, target);
        Files.writeString(marker, pluginVersion);
        plugin.getLogger().info("Installed TerraformGenerator biome bridge datapack → " + target.getFileName());
        return true;
    }

    private void copyResourceTree(String resourceRoot, Path targetDir) throws IOException {
        URI codeSource;
        try {
            codeSource = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (Exception e) {
            throw new IOException("Cannot resolve plugin jar location", e);
        }

        Path jarPath = Path.of(codeSource);
        if (Files.isDirectory(jarPath)) {
            Path root = jarPath.resolve(resourceRoot);
            if (!Files.isDirectory(root)) {
                throw new IOException("Missing resource tree: " + root);
            }
            copyDirectory(root, targetDir);
            return;
        }

        try (FileSystem fs = FileSystems.newFileSystem(jarPath, Collections.emptyMap())) {
            Path root = fs.getPath(resourceRoot);
            if (!Files.isDirectory(root)) {
                root = fs.getPath("/" + resourceRoot);
            }
            if (!Files.isDirectory(root)) {
                throw new IOException("Missing resource tree in jar: " + resourceRoot);
            }
            copyDirectory(root, targetDir);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(dir);
                Path dest = rel.toString().isEmpty() ? target : target.resolve(rel.toString());
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(file);
                Path dest = target.resolve(rel.toString());
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void maybeHintReload() {
        if (!restartHintPrinted.compareAndSet(false, true)) {
            return;
        }
        SchedulerUtil.runGlobal(plugin, () -> plugin.getLogger().warning(
                "TFG biome bridge datapack was installed/updated. Run /minecraft:reload or restart "
                        + "so it becomes active. Isolation filtering works immediately."
        ));
    }

    public String statusSummary() {
        try {
            Path dir = resolveDatapacksDirectory();
            boolean tfg = Files.isDirectory(dir.resolve(TFG_BRIDGE_FOLDER));
            return "datapacksDir=" + dir.toAbsolutePath() + " tfgBridge=" + tfg;
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }
}
