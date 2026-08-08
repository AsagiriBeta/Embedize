package com.embedize.compat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MultiverseAccessNoopTest {

    @Test
    void noopResolvesDirectWorldTokens() {
        MultiverseAccess access = MultiverseAccess.NOOP;
        Assertions.assertFalse(access.isAvailable());
        Assertions.assertEquals("unavailable", access.getVersion());
        Assertions.assertTrue(access.resolveWorldName("resource").isPresent());
        Assertions.assertEquals("resource", access.resolveWorldName("resource").orElseThrow());
        Assertions.assertTrue(access.getWorldAlias("resource").isEmpty());
        Assertions.assertTrue(access.listManagedWorldNames().isEmpty());
        Assertions.assertTrue(access.findSafeLocation(null).isEmpty());
        Assertions.assertFalse(access.detachWorldForReset("resource"));
        Assertions.assertFalse(access.registerLoadedWorld(null, null));
        Assertions.assertFalse(access.configureResourceWorld("resource", "x"));
    }
}
