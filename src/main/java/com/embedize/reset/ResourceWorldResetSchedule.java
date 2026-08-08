package com.embedize.reset;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Pure scheduling helpers (JVM system zone clock supplied by caller).
 */
public final class ResourceWorldResetSchedule {

    private static final Set<String> PROTECTED = Set.of("world", "world_nether", "world_the_end");
    private static final DateTimeFormatter AUTO_KEY = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private ResourceWorldResetSchedule() {
    }

    public static boolean isProtectedWorldName(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return PROTECTED.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public static String autoKey(LocalDateTime now) {
        return now.format(AUTO_KEY);
    }

    public static OptionalLong secondsUntilNextReset(LocalDateTime now, ResourceWorldResetSettings settings) {
        int tgtSec = settings.hour() * 3600 + settings.minute() * 60;
        int curSec = now.toLocalTime().toSecondOfDay();
        LocalDateTime probe = now;
        for (int daysAhead = 0; daysAhead < 400; daysAhead++) {
            if (probe.getDayOfMonth() == settings.dayOfMonth()) {
                if (daysAhead > 0) {
                    return OptionalLong.of((long) daysAhead * 86400L + tgtSec - curSec);
                }
                if (curSec < tgtSec) {
                    return OptionalLong.of(tgtSec - curSec);
                }
            }
            probe = probe.plusDays(1);
        }
        return OptionalLong.empty();
    }

    public static Optional<String> nextResetDay(LocalDateTime now, ResourceWorldResetSettings settings) {
        int tgtSec = settings.hour() * 3600 + settings.minute() * 60;
        int curSec = now.toLocalTime().toSecondOfDay();
        LocalDateTime probe = now;
        for (int daysAhead = 0; daysAhead < 400; daysAhead++) {
            if (probe.getDayOfMonth() == settings.dayOfMonth()) {
                if (daysAhead > 0 || curSec < tgtSec) {
                    return Optional.of(probe.format(DAY));
                }
            }
            probe = probe.plusDays(1);
        }
        return Optional.empty();
    }

    public static String formatCountdownZh(long totalSeconds) {
        if (totalSeconds < 0) {
            return "未知";
        }
        if (totalSeconds < 60) {
            return "不到 1 分钟";
        }
        long days = totalSeconds / 86400;
        long rem = totalSeconds - days * 86400;
        long hours = rem / 3600;
        rem -= hours * 3600;
        long mins = rem / 60;
        return days + "天" + hours + "小时" + mins + "分钟";
    }

    public static String nextFormatted(LocalDateTime now, ResourceWorldResetSettings settings) {
        return nextResetDay(now, settings)
                .map(day -> day + " " + settings.timeLabel())
                .orElse("未知");
    }

    /**
     * @return warn bucket key {@code 30m}/{@code 5m}/{@code 1m}, or empty if outside windows
     */
    public static Optional<String> warnBucket(long secondsLeft) {
        if (secondsLeft >= 1740 && secondsLeft <= 1860) {
            return Optional.of("30m");
        }
        if (secondsLeft >= 240 && secondsLeft <= 360) {
            return Optional.of("5m");
        }
        if (secondsLeft >= 30 && secondsLeft <= 90) {
            return Optional.of("1m");
        }
        return Optional.empty();
    }

    public static boolean isInAutoFireWindow(LocalDateTime now, ResourceWorldResetSettings settings) {
        if (now.getDayOfMonth() != settings.dayOfMonth()) {
            return false;
        }
        int curSec = now.toLocalTime().toSecondOfDay();
        int tgtSec = settings.hour() * 3600 + settings.minute() * 60;
        return curSec >= tgtSec && curSec <= tgtSec + 300;
    }
}
