package com.embedize.command;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.LuckPermsHook;
import com.embedize.reset.ResourceWorldResetSchedule;
import com.embedize.reset.ResourceWorldResetService;
import com.embedize.reset.ResourceWorldResetSettings;
import com.embedize.reset.ResourceWorldResetStateStore;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Standalone commands compatible with the former Skript API:
 * {@code /resreset}, {@code /resresetstatus}, {@code /resresetunlock}.
 */
public final class ResourceResetCommand implements BasicCommand {

    public enum Mode {
        RESET,
        STATUS,
        UNLOCK
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final EmbedizePlugin plugin;
    private final Mode mode;

    public ResourceResetCommand(EmbedizePlugin plugin, Mode mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!canUse(sender)) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        ResourceWorldResetService service = plugin.getResourceWorldResetService();
        if (service == null) {
            sender.sendMessage(Component.text("Resource reset service unavailable.", NamedTextColor.RED));
            return;
        }
        switch (mode) {
            case RESET -> {
                String reason = args.length == 0 ? "" : String.join(" ", args);
                sender.sendMessage(LEGACY.deserialize("&a已提交资源世界重置流程，完成结果会通过聊天广播提示。"));
                service.requestManualReset(reason);
            }
            case STATUS -> sendStatus(sender, service);
            case UNLOCK -> {
                service.forceUnlock();
                sender.sendMessage(LEGACY.deserialize("&a已强制清除资源世界重置运行锁。"));
            }
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        return List.of();
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        return lp.hasAdmin(sender) || sender.hasPermission("embedize.command.reset");
    }

    @Override
    public @Nullable String permission() {
        return "embedize.command.reset";
    }

    public static void sendStatus(CommandSender sender, ResourceWorldResetService service) {
        ResourceWorldResetSettings cfg = service.settings();
        ResourceWorldResetStateStore state = service.state();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        sender.sendMessage(LEGACY.deserialize("&6=== 资源世界重置状态 ==="));

        Instant lastAt = state.lastAt();
        if (lastAt != null) {
            sender.sendMessage(LEGACY.deserialize("&e上次重置时间: &f" + lastAt));
            sender.sendMessage(LEGACY.deserialize("&e距离上次重置: &f" + formatDuration(Duration.between(lastAt, Instant.now()))));
        } else {
            sender.sendMessage(LEGACY.deserialize("&e上次重置时间: &f尚未创建/重置过资源世界"));
            sender.sendMessage(LEGACY.deserialize("&e距离上次重置: &fN/A"));
        }

        String lastGen = state.lastGenerator();
        sender.sendMessage(LEGACY.deserialize("&e上次使用生成器: &f"
                + (lastGen.isBlank() ? "尚未创建/重置过资源世界" : lastGen)));
        String lastReason = state.lastReason();
        if (!lastReason.isBlank()) {
            sender.sendMessage(LEGACY.deserialize("&e上次重置原因: &f" + lastReason));
        }
        Instant lastAutoAt = state.lastAutoAt();
        sender.sendMessage(LEGACY.deserialize("&e上次自动重置时间: &f"
                + (lastAutoAt == null ? "尚未自动重置过" : lastAutoAt.toString())));

        sender.sendMessage(LEGACY.deserialize("&e下次计划重置: &f"
                + ResourceWorldResetSchedule.nextFormatted(now, cfg)));
        OptionalLong left = ResourceWorldResetSchedule.secondsUntilNextReset(now, cfg);
        sender.sendMessage(LEGACY.deserialize("&e倒计时: &f"
                + (left.isPresent()
                ? ResourceWorldResetSchedule.formatCountdownZh(left.getAsLong())
                : "未知")));

        if (service.isRunning()) {
            Instant since = service.runningSince();
            sender.sendMessage(LEGACY.deserialize("&c重置运行中: &f是（阶段：" + service.stage()
                    + "，开始于 " + (since == null ? "?" : since) + "）"));
        } else {
            sender.sendMessage(LEGACY.deserialize("&e重置运行中: &f否"));
        }
        sender.sendMessage(LEGACY.deserialize("&e自动重置计划: &f每月 "
                + String.format(Locale.ROOT, "%02d", cfg.dayOfMonth())
                + " 日 " + cfg.timeLabel()
                + "（现实时间 / JVM 系统时区）；手动重置不推迟自动计划"));
        sender.sendMessage(LEGACY.deserialize("&7世界=" + cfg.world()
                + " 回城=" + cfg.fallbackWorld()
                + " 生成器=" + cfg.generator()
                + " 半径=" + cfg.radius()
                + " enabled=" + cfg.enabled()));
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.getSeconds());
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + mins + "m";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m " + secs + "s";
        }
        if (mins > 0) {
            return mins + "m " + secs + "s";
        }
        return secs + "s";
    }
}
