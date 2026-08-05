package com.dsi.isolator.datapack;

import com.dsi.isolator.DimensionStructureIsolatorPlugin;
import com.dsi.isolator.config.PluginConfig;
import com.dsi.isolator.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class DatapackService {

    public static final String TFG_BRIDGE_FOLDER = "dsi-tfg-biome-bridge";
    public static final String DNT_FOLDER = "dsi-dungeons-and-taverns";

    private final DimensionStructureIsolatorPlugin plugin;
    private final PluginConfig config;
    private final DntDownloader dntDownloader;
    private final AtomicBoolean restartHintPrinted = new AtomicBoolean(false);

    public DatapackService(DimensionStructureIsolatorPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.dntDownloader = new DntDownloader(plugin, config);
    }

    public void ensureInstalled() throws IOException, InterruptedException {
        Path datapacksDir = resolveDatapacksDirectory();
        Files.createDirectories(datapacksDir);

        boolean changed = false;
        if (config.isInstallTfgBridge()) {
            changed |= installTfgBridge(datapacksDir);
        }
        if (config.isDntEnabled() && "auto".equalsIgnoreCase(config.getDntInstallMode())) {
            changed |= installDnt(datapacksDir);
        }

        if (changed) {
            maybeHintReload();
        } else {
            plugin.getLogger().info("Datapack install check complete (no changes) → " + datapacksDir);
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

        // Worlds may not be loaded yet on Folia/async — fall back to world container + level-name
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
        Path marker = target.resolve(".dsi-installed");
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

    private boolean installDnt(Path datapacksDir) throws IOException, InterruptedException {
        Path target = datapacksDir.resolve(DNT_FOLDER);
        Path marker = target.resolve(".dsi-dnt-source");

        Path sourceZip = dntDownloader.ensureCached();
        String sourceKey = sourceZip.getFileName().toString() + ":" + Files.size(sourceZip);

        if (Files.isDirectory(target) && Files.isRegularFile(marker)) {
            String existing = Files.readString(marker).trim();
            if (existing.equals(sourceKey)) {
                return false;
            }
        }

        if (Files.exists(target)) {
            deleteRecursive(target);
        }
        Files.createDirectories(target);
        unzip(sourceZip, target);
        Files.writeString(marker, sourceKey);
        plugin.getLogger().info("Installed Dungeons and Taverns datapack → " + target.getFileName()
                + " (source " + sourceZip.getFileName() + ")");
        return true;
    }

    private void copyResourceTree(String resourceRoot, Path targetDir) throws IOException {
        // Walk jar resources via classloader listing is awkward; copy known tree from jar FileSystem
        URI codeSource;
        try {
            codeSource = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (Exception e) {
            throw new IOException("Cannot resolve plugin jar location", e);
        }

        Path jarPath = Path.of(codeSource);
        if (Files.isDirectory(jarPath)) {
            // Running from classes dir (dev)
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
                // jar paths may lack trailing semantics
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

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(zipFile, Collections.emptyMap())) {
            for (Path root : fs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path rel = root.relativize(dir);
                        if (!rel.toString().isEmpty()) {
                            Files.createDirectories(targetDir.resolve(rel.toString()));
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path rel = root.relativize(file);
                        Path dest = targetDir.resolve(rel.toString());
                        Files.createDirectories(dest.getParent());
                        try (InputStream in = Files.newInputStream(file); OutputStream out = Files.newOutputStream(dest)) {
                            in.transferTo(out);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
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
                "Datapacks were installed/updated. Run /minecraft:reload or restart the server so DnT/TFG bridge become active. "
                        + "Isolation filtering works immediately for already-loaded structure registries."
        ));
    }

    public String statusSummary() {
        try {
            Path dir = resolveDatapacksDirectory();
            boolean tfg = Files.isDirectory(dir.resolve(TFG_BRIDGE_FOLDER));
            boolean dnt = Files.isDirectory(dir.resolve(DNT_FOLDER));
            return "datapacksDir=" + dir.toAbsolutePath()
                    + " tfgBridge=" + tfg
                    + " dnt=" + dnt
                    + " mc=" + DntDownloader.detectMinecraftVersion();
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }

    public boolean forceReinstall() throws IOException, InterruptedException {
        Path datapacksDir = resolveDatapacksDirectory();
        Files.createDirectories(datapacksDir);
        boolean changed = false;
        if (config.isInstallTfgBridge()) {
            Path target = datapacksDir.resolve(TFG_BRIDGE_FOLDER);
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            changed |= installTfgBridge(datapacksDir);
        }
        if (config.isDntEnabled()) {
            Path target = datapacksDir.resolve(DNT_FOLDER);
            if (Files.exists(target)) {
                deleteRecursive(target);
            }
            changed |= installDnt(datapacksDir);
        }
        if (changed) {
            restartHintPrinted.set(false);
            maybeHintReload();
        }
        return changed;
    }
}
