package com.embedize.reset;

import com.embedize.EmbedizePlugin;
import com.embedize.border.WorldBorderData;
import com.embedize.compat.MultiverseAccess;
import com.embedize.util.SchedulerUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Ports the former Skript resource-world reset flow into Embedize.
 * Multiverse unload/remove/register go through typed API (save=false unload);
 * world creation uses Bukkit WorldCreator with keepSpawnLoaded(FALSE).
 */
public final class ResourceWorldResetService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private final EmbedizePlugin plugin;
    private final ResourceWorldResetStateStore state;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ResourceWorldResetSettings settings = ResourceWorldResetSettings.defaults();
    private volatile String stage = "空闲";
    private volatile Instant runningSince;
    private ScheduledTask pollTask;

    public ResourceWorldResetService(EmbedizePlugin plugin) {
        this.plugin = plugin;
        this.state = new ResourceWorldResetStateStore(plugin);
    }

    public synchronized void start() {
        state.load();
        reloadSettings();
        validateConfigBroadcast();
        restartPoll();
    }

    public synchronized void reload() {
        reloadSettings();
        validateConfigBroadcast();
        restartPoll();
    }

    public synchronized void shutdown() {
        stopPoll();
        if (running.get()) {
            unlock();
        }
    }

    public synchronized void reloadSettings() {
        settings = ResourceWorldResetSettings.fromConfig(plugin.getConfig());
    }

    private void restartPoll() {
        stopPoll();
        if (settings.enabled()) {
            // 30s poll, matching the former Skript script
            pollTask = SchedulerUtil.runGlobalTimer(plugin, this::tick, 20L * 30L, 20L * 30L);
            plugin.getLogger().info("Resource world reset enabled for '" + settings.world()
                    + "' (monthly day " + settings.dayOfMonth() + " " + settings.timeLabel() + ").");
        } else {
            plugin.getLogger().info("Resource world reset disabled in config.");
        }
    }

    private void stopPoll() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    public ResourceWorldResetSettings settings() {
        return settings;
    }

    public ResourceWorldResetStateStore state() {
        return state;
    }

    public boolean isRunning() {
        return running.get();
    }

    public String stage() {
        return stage;
    }

    public Instant runningSince() {
        return runningSince;
    }

    public boolean isResourceWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(settings.world());
    }

    public void unlock() {
        running.set(false);
        runningSince = null;
        stage = "空闲";
    }

    public boolean forceUnlock() {
        boolean was = running.getAndSet(false);
        runningSince = null;
        stage = "空闲";
        return was;
    }

    public synchronized boolean requestManualReset(String reasonSuffix) {
        String reason = (reasonSuffix == null || reasonSuffix.isBlank())
                ? "MANUAL"
                : "MANUAL-" + reasonSuffix.trim();
        return beginReset(reason, "");
    }

    private void tick() {
        if (!settings.enabled() || !settings.scheduleEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (!running.get()) {
            OptionalLong left = ResourceWorldResetSchedule.secondsUntilNextReset(now, settings);
            left.ifPresent(this::maybeWarn);
            maybeAutoReset(now);
        }
    }

    private void maybeWarn(long secondsLeft) {
        var bucket = ResourceWorldResetSchedule.warnBucket(secondsLeft);
        if (bucket.isEmpty()) {
            return;
        }
        String stamp = LocalDateTime.now(ZoneId.systemDefault()).format(DAY) + "-" + bucket.get();
        if (stamp.equals(state.warnKey())) {
            return;
        }
        state.setWarnKey(stamp);
        switch (bucket.get()) {
            case "30m" -> broadcast("&6[资源世界] 30 分钟后将进行月度重置，请及时把物资带回主世界。");
            case "5m" -> broadcast("&6[资源世界] 5 分钟后将进行月度重置，请立即撤离资源世界！");
            default -> broadcast("&c[资源世界] 1 分钟后重置，未撤离的玩家将被强制传送回主世界。");
        }
    }

    private void maybeAutoReset(LocalDateTime now) {
        if (!ResourceWorldResetSchedule.isInAutoFireWindow(now, settings)) {
            return;
        }
        String autoKey = ResourceWorldResetSchedule.autoKey(now);
        if (autoKey.equals(state.lastAutoKey())) {
            return;
        }
        // Claim key before start so a failed attempt does not spam every 30s in the window.
        state.markAutoKey(autoKey);
        beginReset("AUTO_MONTHLY", autoKey);
    }

    private synchronized boolean beginReset(String reason, String autoKey) {
        ResourceWorldResetSettings cfg = settings;
        if (!cfg.enabled()) {
            broadcast("&c[资源世界] 重置已在配置中禁用（resource-reset.enabled=false）。");
            return false;
        }
        if (ResourceWorldResetSchedule.isProtectedWorldName(cfg.world())) {
            broadcast("&c[资源世界] 安全中止：resourceWorld 不能是受保护世界（world/world_nether/world_the_end）。");
            return false;
        }
        if (cfg.world().equalsIgnoreCase(cfg.fallbackWorld())) {
            broadcast("&c[资源世界] 安全中止：resourceWorld 与 fallbackWorld 不能相同。");
            return false;
        }
        World safeWorld = Bukkit.getWorld(cfg.fallbackWorld());
        if (safeWorld == null) {
            broadcast("&c[资源世界] 重置失败：找不到回城世界 " + cfg.fallbackWorld() + "。");
            return false;
        }
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) {
            broadcast("&c[资源世界] 重置失败：未安装 Multiverse-Core。");
            return false;
        }

        if (running.get()) {
            if (!isLockStale(cfg)) {
                broadcast("&c[资源世界] 已有重置流程正在运行（阶段：" + stage + "），本次请求已跳过。");
                return false;
            }
            broadcast("&e[资源世界] 检测到超过 " + cfg.lockTimeoutMinutes() + " 分钟的残留运行锁，已强制解除。");
            unlock();
        }

        if (!running.compareAndSet(false, true)) {
            broadcast("&c[资源世界] 已有重置流程正在运行，本次请求已跳过。");
            return false;
        }
        runningSince = Instant.now();
        stage = "准备中";
        broadcast("&e[资源世界] 开始重置：原因=" + reason + " | 生成器=" + cfg.generator());
        SchedulerUtil.runGlobal(plugin, () -> stageEvacuate(reason, autoKey, cfg, safeWorld));
        return true;
    }

    private boolean isLockStale(ResourceWorldResetSettings cfg) {
        Instant since = runningSince;
        if (since == null) {
            return true;
        }
        return Duration.between(since, Instant.now()).toMinutes() >= cfg.lockTimeoutMinutes();
    }

    private void stageEvacuate(String reason, String autoKey, ResourceWorldResetSettings cfg, World safeWorld) {
        if (!running.get()) {
            return;
        }
        stage = "清场";
        World target = Bukkit.getWorld(cfg.world());
        if (target != null) {
            // Only evacuate players who are actually in the resource world.
            // Never touch players already in the fallback/main world.
            int moved = evacuateResourcePlayers(target, safeWorld);
            if (moved > 0) {
                broadcast("&e[资源世界] 已将 " + moved + " 名玩家送回主世界。");
            }
            dispatch("execute in minecraft:" + cfg.world() + " run forceload remove all");
            stage = "等待撤离";
            broadcast("&7[资源世界] 确认资源世界已空，并等待区块静默（" + cfg.settleSeconds() + " 秒）…");
            long settleTicks = Math.max(1L, cfg.settleSeconds() * 20L);
            waitUntilEmptyThen(target.getName(), safeWorld, settleTicks, 0,
                    () -> stageUnload(reason, autoKey, cfg));
            return;
        }
        stageUnload(reason, autoKey, cfg);
    }

    /**
     * Teleport every player still in {@code target} to {@code safeWorld}.
     * Uses entity/region schedulers so Folia finishes the teleport before unload.
     */
    private int evacuateResourcePlayers(World target, World safeWorld) {
        int moved = 0;
        Location safe = safeWorld.getSpawnLocation();
        for (Player player : List.copyOf(target.getPlayers())) {
            // Defensive: never teleport someone who is not in the resource world.
            if (player.getWorld() == null || !player.getWorld().equals(target)) {
                continue;
            }
            final Player p = player;
            SchedulerUtil.runForEntity(plugin, p, () -> {
                if (p.getWorld() != null && p.getWorld().equals(target)) {
                    p.teleportAsync(safe).thenAccept(ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            p.sendMessage(LEGACY.deserialize(
                                    "&e[资源世界] 资源世界正在重置，你已被传送回主世界。"));
                        } else {
                            plugin.getLogger().warning("[resource-reset] teleportAsync failed for "
                                    + p.getName() + "; will retry before unload");
                        }
                    });
                }
            });
            moved++;
        }
        return moved;
    }

    private void waitUntilEmptyThen(
            String worldName,
            World safeWorld,
            long settleTicks,
            int attempt,
            Runnable next
    ) {
        if (!running.get()) {
            return;
        }
        World target = Bukkit.getWorld(worldName);
        if (target == null) {
            SchedulerUtil.runGlobalLater(plugin, next, Math.max(1L, settleTicks));
            return;
        }
        int remaining = target.getPlayers().size();
        if (remaining > 0) {
            if (attempt >= 40) {
                broadcast("&c[资源世界] 仍有 " + remaining + " 名玩家滞留，强制再送一次后继续卸载。");
                evacuateResourcePlayers(target, safeWorld);
                SchedulerUtil.runGlobalLater(plugin, () -> {
                    World still = Bukkit.getWorld(worldName);
                    if (still != null && !still.getPlayers().isEmpty()) {
                        plugin.getLogger().warning("[resource-reset] players still in "
                                + worldName + " after forced evacuate: "
                                + still.getPlayers().stream().map(Player::getName).toList());
                    }
                    SchedulerUtil.runGlobalLater(plugin, next, Math.max(1L, settleTicks));
                }, 40L);
                return;
            }
            if (attempt % 5 == 0) {
                evacuateResourcePlayers(target, safeWorld);
                plugin.getLogger().info("[resource-reset] waiting for " + remaining
                        + " player(s) to leave '" + worldName + "' (attempt " + attempt + ")");
            }
            SchedulerUtil.runGlobalLater(plugin,
                    () -> waitUntilEmptyThen(worldName, safeWorld, settleTicks, attempt + 1, next),
                    10L);
            return;
        }
        stage = "静默等待";
        SchedulerUtil.runGlobalLater(plugin, next, Math.max(1L, settleTicks));
    }

    private void stageUnload(String reason, String autoKey, ResourceWorldResetSettings cfg) {
        if (!running.get()) {
            return;
        }
        stage = "卸载";
        World target = Bukkit.getWorld(cfg.world());
        if (target == null) {
            // Not Bukkit-loaded. Drop leftover MV config if present, then delete folder.
            MultiverseAccess mv = plugin.getMultiverseHook().access();
            if (mv.isAvailable() && mv.isManagedWorld(cfg.world())) {
                mv.detachWorldForReset(cfg.world());
            }
            stageDelete(reason, autoKey, cfg);
            return;
        }
        if (!target.getPlayers().isEmpty()) {
            plugin.getLogger().warning("[resource-reset] refusing unload — "
                    + target.getPlayers().size() + " player(s) still in " + cfg.world());
            World safe = Bukkit.getWorld(cfg.fallbackWorld());
            if (safe != null) {
                evacuateResourcePlayers(target, safe);
            }
            SchedulerUtil.runGlobalLater(plugin, () -> stageUnload(reason, autoKey, cfg), 40L);
            return;
        }
        // Never console `mv unload` (save=true by default): large resource worlds
        // block the Server thread for minutes and keepalive-kick everyone.
        requestUnloadNoSave(cfg);
        pollUnload(reason, autoKey, cfg, 0);
    }

    /** Unload without flushing chunks — reset deletes the folder next. */
    private void requestUnloadNoSave(ResourceWorldResetSettings cfg) {
        World target = Bukkit.getWorld(cfg.world());
        if (target == null) {
            return;
        }
        MultiverseAccess mv = plugin.getMultiverseHook().access();
        long startedNs = System.nanoTime();
        if (mv.isAvailable() && mv.isManagedWorld(cfg.world())) {
            boolean detached = mv.detachWorldForReset(cfg.world());
            long ms = (System.nanoTime() - startedNs) / 1_000_000L;
            plugin.getLogger().info("[resource-reset] detachWorldForReset('" + cfg.world()
                    + "') => " + detached + " in " + ms + " ms");
            if (Bukkit.getWorld(cfg.world()) == null) {
                return;
            }
            plugin.getLogger().warning("[resource-reset] Multiverse detach left Bukkit world loaded; "
                    + "falling back to Bukkit.unloadWorld(save=false)");
        }
        boolean ok = Bukkit.unloadWorld(target, false);
        long ms = (System.nanoTime() - startedNs) / 1_000_000L;
        plugin.getLogger().info("[resource-reset] Bukkit.unloadWorld('" + cfg.world()
                + "', save=false) => " + ok + " in " + ms + " ms");
        if (ms >= 5_000L) {
            plugin.getLogger().warning("[resource-reset] unload blocked the server thread for "
                    + ms + " ms — online clients may have timed out");
        }
    }

    private void pollUnload(String reason, String autoKey, ResourceWorldResetSettings cfg, int attempt) {
        if (!running.get()) {
            return;
        }
        if (Bukkit.getWorld(cfg.world()) == null) {
            stageDelete(reason, autoKey, cfg);
            return;
        }
        if (attempt >= 30) {
            World still = Bukkit.getWorld(cfg.world());
            int players = still == null ? 0 : still.getPlayers().size();
            broadcast("&c[资源世界] 卸载失败：" + cfg.world()
                    + " 仍处于加载状态（players=" + players
                    + ", attempts=" + attempt
                    + "）。已中止本次重置（未删除任何数据）。");
            unlock();
            return;
        }
        if (attempt > 0) {
            requestUnloadNoSave(cfg);
        }
        SchedulerUtil.runGlobalLater(plugin, () -> pollUnload(reason, autoKey, cfg, attempt + 1), 20L);
    }

    private void stageDelete(String reason, String autoKey, ResourceWorldResetSettings cfg) {
        if (!running.get()) {
            return;
        }
        stage = "删除";
        MultiverseAccess mv = plugin.getMultiverseHook().access();
        if (mv.isAvailable() && mv.isManagedWorld(cfg.world())) {
            boolean removed = mv.detachWorldForReset(cfg.world());
            plugin.getLogger().info("[resource-reset] leftover MV config remove => " + removed);
        }
        long startedNs = System.nanoTime();
        deleteWorldFolder(cfg.world());
        long ms = (System.nanoTime() - startedNs) / 1_000_000L;
        plugin.getLogger().info("[resource-reset] world folder delete took " + ms + " ms");
        if (ms >= 5_000L) {
            plugin.getLogger().warning("[resource-reset] folder delete blocked the server thread for "
                    + ms + " ms");
        }
        SchedulerUtil.runGlobalLater(plugin, () -> stageRecreate(reason, autoKey, cfg), 40L);
    }

    private void deleteWorldFolder(String worldName) {
        Path folder = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.isDirectory(folder)) {
            plugin.getLogger().info("[resource-reset] world folder already absent: " + folder);
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    plugin.getLogger().warning("[resource-reset] could not delete " + path + ": " + ex.getMessage());
                }
            }
            plugin.getLogger().info("[resource-reset] deleted world folder " + folder);
        } catch (IOException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[resource-reset] failed walking world folder " + folder, ex);
        }
    }

    private void stageRecreate(String reason, String autoKey, ResourceWorldResetSettings cfg) {
        if (!running.get()) {
            return;
        }
        stage = "重建";
        // Quantified in latest.log (MANUAL-RCON-VERIFY2):
        //   WorldCreator: Prepared spawn 0–1ms
        //   console mv import: 25–41s (Multiverse getBlock on spawn → full Embedize chunk)
        //   player lost connection immediately after import returned
        // Fix: WorldCreator + API import under ResetSpawnFastPath (cheap 0,0 platform).
        broadcast("&7[资源世界] 正在重建世界（固定出生点、不预加载出生区块）…");
        final String usedGen = cfg.usePluginGenerator() ? cfg.generator() : "vanilla";
        long startedNs = System.nanoTime();
        boolean created = createResourceWorldFast(cfg);
        long createMs = (System.nanoTime() - startedNs) / 1_000_000L;
        plugin.getLogger().info("[resource-reset] WorldCreator returned after " + createMs + " ms");

        long registerMs = 0L;
        if (!created) {
            plugin.getLogger().warning("[resource-reset] fast WorldCreator path failed; falling back to mv create");
            long fbNs = System.nanoTime();
            dispatch(buildCreateCommand(cfg));
            registerMs = (System.nanoTime() - fbNs) / 1_000_000L;
        } else {
            World world = Bukkit.getWorld(cfg.world());
            MultiverseAccess mv = plugin.getMultiverseHook().access();
            if (world != null && mv.isAvailable()) {
                com.embedize.terrain.ResetSpawnFastPath.enable();
                try {
                    long regNs = System.nanoTime();
                    boolean registered = mv.registerLoadedWorld(world,
                            cfg.usePluginGenerator() ? cfg.generator() : null);
                    registerMs = (System.nanoTime() - regNs) / 1_000_000L;
                    plugin.getLogger().info("[resource-reset] Multiverse registerLoadedWorld => "
                            + registered + " in " + registerMs + " ms");
                } finally {
                    com.embedize.terrain.ResetSpawnFastPath.disable();
                }
            } else {
                plugin.getLogger().info("[resource-reset] skipping Multiverse register "
                        + "(world=" + (world != null) + ", mv=" + mv.isAvailable() + ")");
            }
        }
        long totalMs = (System.nanoTime() - startedNs) / 1_000_000L;
        plugin.getLogger().info("[resource-reset] recreate phase total " + totalMs
                + " ms (create=" + createMs + "ms, register=" + registerMs + "ms)");
        if (totalMs >= 5_000L) {
            plugin.getLogger().warning("[resource-reset] recreate blocked the server thread for "
                    + totalMs + " ms — online clients may have timed out");
        }
        SchedulerUtil.runGlobalLater(plugin, () -> pollReady(reason, autoKey, cfg, usedGen, 0), 20L);
    }

    /**
     * Create via Bukkit/Paper {@link WorldCreator} with spawn-chunk loading disabled.
     * Embedize generators also return {@code getFixedSpawnLocation} so Paper skips biome scans.
     */
    private boolean createResourceWorldFast(ResourceWorldResetSettings cfg) {
        if (Bukkit.getWorld(cfg.world()) != null) {
            return true;
        }
        try {
            WorldCreator creator = new WorldCreator(cfg.world());
            creator.environment(World.Environment.NORMAL);
            if (cfg.usePluginGenerator()) {
                ChunkGenerator generator = plugin.getDefaultWorldGenerator(cfg.world(), null);
                if (generator != null) {
                    creator.generator(generator);
                } else {
                    creator.generator(cfg.generator());
                }
            }
            @SuppressWarnings("removal")
            WorldCreator loaded = creator.keepSpawnLoaded(TriState.FALSE);
            World world = loaded.createWorld();
            if (world == null) {
                return false;
            }
            int spawnY = Math.min(world.getMaxHeight() - 8,
                    Math.max(world.getMinHeight() + 16, 96));
            world.setSpawnLocation(0, spawnY, 0);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[resource-reset] WorldCreator failed for '" + cfg.world() + "'", ex);
            return false;
        }
    }

    static String buildCreateCommand(ResourceWorldResetSettings cfg) {
        StringBuilder sb = new StringBuilder("mv create ")
                .append(cfg.world())
                .append(" normal");
        if (cfg.usePluginGenerator()) {
            sb.append(" --generator ").append(cfg.generator());
        }
        sb.append(" --no-adjust-spawn --force-spawn-position 0,96,0");
        return sb.toString();
    }

    static String buildImportCommand(ResourceWorldResetSettings cfg) {
        StringBuilder sb = new StringBuilder("mv import ")
                .append(cfg.world())
                .append(" normal");
        if (cfg.usePluginGenerator()) {
            sb.append(" --generator ").append(cfg.generator());
        }
        sb.append(" --no-adjust-spawn");
        return sb.toString();
    }

    private void pollReady(String reason, String autoKey, ResourceWorldResetSettings cfg, String usedGen, int attempt) {
        if (!running.get()) {
            return;
        }
        if (Bukkit.getWorld(cfg.world()) != null) {
            stageConfigure(reason, autoKey, cfg, usedGen);
            return;
        }
        if (attempt >= 29) {
            broadcast("&c[资源世界] 世界创建超时：" + cfg.world() + " 未在预期时间内加载，请手动检查。");
            unlock();
            return;
        }
        if (!createResourceWorldFast(cfg)) {
            dispatch("mv load " + cfg.world());
        }
        SchedulerUtil.runGlobalLater(plugin, () -> pollReady(reason, autoKey, cfg, usedGen, attempt + 1), 20L);
    }

    private void stageConfigure(String reason, String autoKey, ResourceWorldResetSettings cfg, String usedGen) {
        if (!running.get()) {
            return;
        }
        stage = "配置";
        MultiverseAccess mv = plugin.getMultiverseHook().access();
        World created = Bukkit.getWorld(cfg.world());
        if (mv.isAvailable() && created != null && !mv.isManagedWorld(cfg.world())) {
            com.embedize.terrain.ResetSpawnFastPath.enable();
            try {
                mv.registerLoadedWorld(created, cfg.usePluginGenerator() ? cfg.generator() : null);
            } finally {
                com.embedize.terrain.ResetSpawnFastPath.disable();
            }
        }
        if (mv.isAvailable() && mv.isManagedWorld(cfg.world())) {
            if (!mv.configureResourceWorld(cfg.world(), cfg.alias())) {
                dispatch("mv modify " + cfg.world() + " set alias " + cfg.alias());
                dispatch("mv modify " + cfg.world() + " set adjust-spawn false");
                dispatch("mv modify " + cfg.world() + " set keep-spawn-in-memory false");
            }
        } else {
            plugin.getLogger().warning("[resource-reset] '" + cfg.world()
                    + "' not Multiverse-managed after recreate; Embedize border still applied");
        }
        plugin.getBorderManager().setBorder(cfg.world(), new WorldBorderData(0.0, 0.0, cfg.radius()));
        plugin.getBorderManager().onWorldReady(cfg.world());

        state.recordSuccess(reason, usedGen, autoKey);
        unlock();
        broadcast("&a[资源世界] 重置完成：新世界=" + cfg.world()
                + " | 生成器=" + usedGen
                + " | Embedize 边界半径=" + cfg.radius());
        broadcast("&7[资源世界] 结构由原版 Start + Embedize placeInChunk 桥接写入；仅新区块生效。");
        if (created != null) {
            int spawnY = Math.min(created.getMaxHeight() - 8,
                    Math.max(created.getMinHeight() + 16, 96));
            created.setSpawnLocation(0, spawnY, 0);
            plugin.getTerrainWorldListener().refreshWorld(created);
        }
    }

    private void validateConfigBroadcast() {
        ResourceWorldResetSettings cfg = settings;
        if (!cfg.enabled()) {
            return;
        }
        if (ResourceWorldResetSchedule.isProtectedWorldName(cfg.world())) {
            broadcast("&c[资源世界] 配置错误：resource-reset.world 当前是受保护世界名，请立即修正。");
        }
        if (cfg.world().equalsIgnoreCase(cfg.fallbackWorld())) {
            broadcast("&c[资源世界] 配置错误：resourceWorld 与 fallbackWorld 不能相同，请立即修正。");
        }
    }

    private boolean dispatch(String command) {
        plugin.getLogger().info("[resource-reset] console: " + command);
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void broadcast(String legacy) {
        Bukkit.getServer().broadcast(LEGACY.deserialize(legacy));
    }

    public String describeStatusLines() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        ResourceWorldResetSettings cfg = settings;
        StringBuilder sb = new StringBuilder();
        sb.append("world=").append(cfg.world())
                .append(" enabled=").append(cfg.enabled())
                .append(" schedule=").append(cfg.scheduleEnabled()
                        ? ("每月" + cfg.dayOfMonth() + "日 " + cfg.timeLabel())
                        : "off")
                .append(" running=").append(running.get())
                .append(running.get() ? ("(" + stage + ")") : "")
                .append(" next=").append(ResourceWorldResetSchedule.nextFormatted(now, cfg));
        OptionalLong left = ResourceWorldResetSchedule.secondsUntilNextReset(now, cfg);
        sb.append(" cd=").append(left.isPresent()
                ? ResourceWorldResetSchedule.formatCountdownZh(left.getAsLong())
                : "未知");
        return sb.toString();
    }
}
