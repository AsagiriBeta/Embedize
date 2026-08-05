package com.embedize.structure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class DecideWithFlagsTest {

    private IsolationPolicy policy() {
        return new IsolationPolicy(
                true,
                true,
                IsolationPolicy.Mode.ALLOWLIST,
                IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT,
                Set.of("resource"),
                Set.of("world", "world_nether", "world_the_end"),
                Set.of("nova_structures"),
                true
        );
    }

    @Test
    void resourceAllowsDefaultWorldDenies() {
        IsolationPolicy p = policy();
        Assertions.assertEquals(
                IsolationPolicy.Decision.ALLOW,
                p.decideWithFlags("nova_structures", "tavern", false, true)
        );
        Assertions.assertEquals(
                IsolationPolicy.Decision.DENY,
                p.decideWithFlags("nova_structures", "tavern", true, false)
        );
        Assertions.assertEquals(
                IsolationPolicy.Decision.DENY,
                p.decideWithFlags("nova_structures", "tavern", false, false)
        );
    }

    @Test
    void sealedWinsEvenIfListed() {
        IsolationPolicy p = policy();
        Assertions.assertEquals(
                IsolationPolicy.Decision.DENY,
                p.decideWithFlags("any_pack", "x", true, true)
        );
    }

    @Test
    void vanillaPasses() {
        IsolationPolicy p = policy();
        Assertions.assertEquals(
                IsolationPolicy.Decision.PASS,
                p.decideWithFlags("minecraft", "village_plains", true, false)
        );
    }
}
