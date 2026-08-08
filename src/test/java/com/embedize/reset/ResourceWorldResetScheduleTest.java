package com.embedize.reset;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldResetScheduleTest {

    private final ResourceWorldResetSettings settings = new ResourceWorldResetSettings(
            true, "resource", "资源世界", "world", "Embedize", true,
            10000, 5, 20, true, 1, 4, 0
    );

    @Test
    void protectsVanillaWorldNames() {
        assertTrue(ResourceWorldResetSchedule.isProtectedWorldName("world"));
        assertTrue(ResourceWorldResetSchedule.isProtectedWorldName("WORLD_NETHER"));
        assertTrue(ResourceWorldResetSchedule.isProtectedWorldName("world_the_end"));
        assertFalse(ResourceWorldResetSchedule.isProtectedWorldName("resource"));
    }

    @Test
    void autoKeyIsYearMonth() {
        assertEquals("2026-08", ResourceWorldResetSchedule.autoKey(LocalDateTime.of(2026, 8, 7, 12, 0)));
    }

    @Test
    void secondsUntilNextResetSameDayBeforeTarget() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 3, 0, 0);
        OptionalLong left = ResourceWorldResetSchedule.secondsUntilNextReset(now, settings);
        assertTrue(left.isPresent());
        assertEquals(3600L, left.getAsLong());
    }

    @Test
    void secondsUntilNextResetAfterWindowGoesNextMonth() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 5, 0, 0);
        OptionalLong left = ResourceWorldResetSchedule.secondsUntilNextReset(now, settings);
        assertTrue(left.isPresent());
        // Aug 1 05:00 → Sep 1 04:00 = 31d - 1h
        assertEquals(31L * 86400L - 3600L, left.getAsLong());
    }

    @Test
    void warnBuckets() {
        assertEquals("30m", ResourceWorldResetSchedule.warnBucket(1800).orElse(""));
        assertEquals("5m", ResourceWorldResetSchedule.warnBucket(300).orElse(""));
        assertEquals("1m", ResourceWorldResetSchedule.warnBucket(60).orElse(""));
        assertTrue(ResourceWorldResetSchedule.warnBucket(1000).isEmpty());
    }

    @Test
    void autoFireWindow() {
        assertTrue(ResourceWorldResetSchedule.isInAutoFireWindow(
                LocalDateTime.of(2026, 9, 1, 4, 0, 0), settings));
        assertTrue(ResourceWorldResetSchedule.isInAutoFireWindow(
                LocalDateTime.of(2026, 9, 1, 4, 4, 0), settings));
        assertFalse(ResourceWorldResetSchedule.isInAutoFireWindow(
                LocalDateTime.of(2026, 9, 1, 3, 59, 0), settings));
        assertFalse(ResourceWorldResetSchedule.isInAutoFireWindow(
                LocalDateTime.of(2026, 9, 1, 4, 6, 0), settings));
        assertFalse(ResourceWorldResetSchedule.isInAutoFireWindow(
                LocalDateTime.of(2026, 9, 2, 4, 0, 0), settings));
    }

    @Test
    void countdownZh() {
        assertEquals("不到 1 分钟", ResourceWorldResetSchedule.formatCountdownZh(45));
        assertEquals("1天2小时3分钟", ResourceWorldResetSchedule.formatCountdownZh(86400 + 7200 + 180));
    }
}
