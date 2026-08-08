package com.embedize.reset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldResetCreateCommandTest {

    @Test
    void buildCreateCommandDisablesSpawnAdjustAndForcesSpawn() {
        ResourceWorldResetSettings cfg = ResourceWorldResetSettings.defaults();
        String cmd = ResourceWorldResetService.buildCreateCommand(cfg);
        assertEquals(
                "mv create resource normal --generator Embedize --no-adjust-spawn --force-spawn-position 0,96,0",
                cmd);
        assertFalse(cmd.contains("--remove-players"));
    }

    @Test
    void buildImportCommandRegistersWorldWithoutSpawnSearch() {
        ResourceWorldResetSettings cfg = ResourceWorldResetSettings.defaults();
        String cmd = ResourceWorldResetService.buildImportCommand(cfg);
        assertEquals(
                "mv import resource normal --generator Embedize --no-adjust-spawn",
                cmd);
    }

    @Test
    void buildCreateCommandWithoutPluginGeneratorStillSkipsSpawnSearch() {
        ResourceWorldResetSettings cfg = new ResourceWorldResetSettings(
                true, "resource", "资源世界", "world", "Embedize", false,
                10000, 5, 20, true, 1, 4, 0);
        String cmd = ResourceWorldResetService.buildCreateCommand(cfg);
        assertTrue(cmd.startsWith("mv create resource normal "));
        assertFalse(cmd.contains("--generator"));
        assertTrue(cmd.contains("--no-adjust-spawn"));
        assertTrue(cmd.contains("--force-spawn-position 0,96,0"));
    }
}
