package com.embedize.compat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class MultiverseAccessNoopTest {

    @Test
    void noopMatchesExactWorldNamesOnly() {
        MultiverseAccess access = MultiverseAccess.NOOP;
        Assertions.assertFalse(access.isAvailable());
        Assertions.assertEquals("unavailable", access.getVersion());
        Assertions.assertTrue(access.matchesConfiguredWorld("resource", List.of("resource", "spawn")));
        Assertions.assertFalse(access.matchesConfiguredWorld("world", List.of("resource")));
        Assertions.assertTrue(access.listManagedWorldNames().isEmpty());
        Assertions.assertTrue(access.findSafeLocation(null).isEmpty());
    }
}
