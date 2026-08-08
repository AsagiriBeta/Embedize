package com.embedize.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SchedulerUtilTest {

    @Test
    void foliaDetectionDoesNotThrow() {
        assertDoesNotThrow(SchedulerUtil::isFolia);
    }
}
