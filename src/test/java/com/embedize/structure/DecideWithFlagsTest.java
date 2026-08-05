package com.embedize.structure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class DecideWithFlagsTest {

    private IsolationPolicy policy() {
        return new IsolationPolicy(
                true,
                IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT,
                Set.of("resource"),
                Set.of("nova_structures"),
                true
        );
    }

    @Test
    void listedAllowsUnlistedDenies() {
        IsolationPolicy p = policy();
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, p.decideWithFlags("nova_structures", "tavern", true));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, p.decideWithFlags("nova_structures", "tavern", false));
    }

    @Test
    void vanillaPasses() {
        IsolationPolicy p = policy();
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, p.decideWithFlags("minecraft", "village_plains", false));
    }
}
