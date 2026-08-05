package com.embedize.datapack;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionUtilTest {

    @Test
    void normalizesPaperBuildSuffix() {
        Assertions.assertEquals("26.1.2", VersionUtil.normalizeVersion("26.1.2.build.63-stable"));
        Assertions.assertEquals("1.21.4", VersionUtil.normalizeVersion("1.21.4-R0.1-SNAPSHOT"));
    }

    @Test
    void matchesFamilies() {
        Assertions.assertTrue(VersionUtil.versionMatches("26.1.2", "26.1"));
        Assertions.assertTrue(VersionUtil.versionMatches("26.1.2", "26.1.x"));
        Assertions.assertTrue(VersionUtil.versionMatches("26.2", "26.2"));
        Assertions.assertTrue(VersionUtil.versionMatches("1.21.4", "1.21.x"));
        Assertions.assertTrue(VersionUtil.versionMatches("1.21.11", "1.21.11"));
        Assertions.assertFalse(VersionUtil.versionMatches("26.1.2", "26.2"));
    }
}
