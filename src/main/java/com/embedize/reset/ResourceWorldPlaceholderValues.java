package com.embedize.reset;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Values formerly exposed by {@code resource_world_reset_placeholders.sk}
 * as {@code %resource_reset_*%}.
 */
public final class ResourceWorldPlaceholderValues {

    public record Snapshot(
            String nextFormatted,
            String countdownZh,
            long days,
            long hours,
            long mins
    ) {
    }

    private ResourceWorldPlaceholderValues() {
    }

    public static Snapshot compute(ResourceWorldResetSettings settings) {
        return compute(settings, LocalDateTime.now(ZoneId.systemDefault()));
    }

    public static Snapshot compute(ResourceWorldResetSettings settings, LocalDateTime now) {
        if (settings == null) {
            return unknown();
        }
        OptionalLong left = ResourceWorldResetSchedule.secondsUntilNextReset(now, settings);
        if (left.isEmpty()) {
            return unknown();
        }
        long total = left.getAsLong();
        long days = Math.max(0L, total / 86400L);
        long rem = total - days * 86400L;
        long hours = Math.max(0L, rem / 3600L);
        rem -= hours * 3600L;
        long mins = Math.max(0L, rem / 60L);
        return new Snapshot(
                ResourceWorldResetSchedule.nextFormatted(now, settings),
                ResourceWorldResetSchedule.formatCountdownZh(total),
                days,
                hours,
                mins
        );
    }

    /**
     * Resolve a skript-compatible param ({@code reset_next}, {@code reset_countdown}, …).
     *
     * @return value, or {@code null} if the param is not recognized
     */
    public static String resolve(Snapshot snapshot, String params) {
        if (snapshot == null || params == null || params.isBlank()) {
            return null;
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "reset_next" -> snapshot.nextFormatted();
            case "reset_countdown" -> snapshot.countdownZh();
            case "reset_days" -> Long.toString(snapshot.days());
            case "reset_hours" -> Long.toString(snapshot.hours());
            case "reset_mins" -> Long.toString(snapshot.mins());
            default -> null;
        };
    }

    private static Snapshot unknown() {
        return new Snapshot("未知", "未知", 0L, 0L, 0L);
    }
}
