package com.embedize.structure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class IsolationPolicyTest {

    private IsolationPolicy whitelist(String allowed, IsolationPolicy.StructureFilterMode filter) {
        return new IsolationPolicy(
                true,
                filter,
                Set.of(allowed),
                Set.of("nova_structures", "other_pack"),
                true
        );
    }

    @Test
    void onlyWhitelistedWorldGetsStructures() {
        IsolationPolicy policy = whitelist("resource", IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT);

        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, policy.decide("resource", "nova_structures", "tavern"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("world", "nova_structures", "tavern"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("somewhere_else", "other_pack", "ruin"));
    }

    @Test
    void worldOnWhitelistResourceOff() {
        IsolationPolicy policy = whitelist("world", IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT);
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, policy.decide("world", "nova_structures", "x"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("resource", "nova_structures", "x"));
    }

    @Test
    void vanillaPassesUnlessFilterAll() {
        IsolationPolicy nonMc = whitelist("resource", IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT);
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, nonMc.decide("world", "minecraft", "village_plains"));

        IsolationPolicy all = whitelist("resource", IsolationPolicy.StructureFilterMode.ALL);
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, all.decide("world", "minecraft", "village_plains"));
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, all.decide("resource", "minecraft", "village_plains"));
    }

    @Test
    void emptyAllowlistDeniesEverywhere() {
        IsolationPolicy policy = new IsolationPolicy(
                true,
                IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT,
                Set.of(),
                Set.of(),
                true
        );
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("resource", "nova_structures", "x"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decideWithFlags("nova_structures", "x", false));
    }

    @Test
    void unresolvedKeyDenied() {
        IsolationPolicy policy = whitelist("resource", IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT);
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("world", null, null));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decideWithFlags(null, null, true));
    }
}
