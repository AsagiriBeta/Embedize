package com.embedize.reset;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourceWorldPlaceholderValuesTest {

    private final ResourceWorldResetSettings settings = new ResourceWorldResetSettings(
            true, "resource", "资源世界", "world", "Embedize", true,
            10000, 5, 20, true, 1, 4, 0
    );

    @Test
    void resolvesSkriptCompatibleParams() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 3, 0, 0);
        ResourceWorldPlaceholderValues.Snapshot snap =
                ResourceWorldPlaceholderValues.compute(settings, now);

        assertEquals("2026-08-01 04:00", ResourceWorldPlaceholderValues.resolve(snap, "reset_next"));
        assertEquals("0天1小时0分钟", ResourceWorldPlaceholderValues.resolve(snap, "reset_countdown"));
        assertEquals("0", ResourceWorldPlaceholderValues.resolve(snap, "reset_days"));
        assertEquals("1", ResourceWorldPlaceholderValues.resolve(snap, "reset_hours"));
        assertEquals("0", ResourceWorldPlaceholderValues.resolve(snap, "reset_mins"));
        assertNull(ResourceWorldPlaceholderValues.resolve(snap, "unknown"));
    }

    @Test
    void underOneMinuteCountdown() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 3, 59, 30);
        ResourceWorldPlaceholderValues.Snapshot snap =
                ResourceWorldPlaceholderValues.compute(settings, now);
        assertEquals("不到 1 分钟", snap.countdownZh());
        assertEquals("0", ResourceWorldPlaceholderValues.resolve(snap, "reset_days"));
        assertEquals("0", ResourceWorldPlaceholderValues.resolve(snap, "reset_hours"));
        assertEquals("0", ResourceWorldPlaceholderValues.resolve(snap, "reset_mins"));
    }
}
