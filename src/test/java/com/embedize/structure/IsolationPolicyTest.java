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
                Set.of("nova_structures", "other_pack", "minecraft"),
                false
        );
    }

    @Test
    void onlyWhitelistedWorldGetsDatapackStructures() {
        IsolationPolicy policy = whitelist("resource", IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT);
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, policy.decide("resource", "nova_structures", "tavern"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("world", "nova_structures", "tavern"));
    }

    @Test
    void vanillaAlwaysPassesInEveryWorld() {
        IsolationPolicy policy = whitelist("resource", IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT);
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decide("world", "minecraft", "village_plains"));
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decide("resource", "minecraft", "stronghold"));
        Assertions.assertFalse(policy.isManagedNamespace("minecraft"));
    }

    @Test
    void minecraftInNamespacesListIsIgnored() {
        IsolationPolicy policy = new IsolationPolicy(
                true,
                IsolationPolicy.StructureFilterMode.NAMESPACES,
                Set.of("resource"),
                Set.of("minecraft", "nova_structures"),
                false
        );
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decide("world", "minecraft", "monument"));
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, policy.decide("resource", "nova_structures", "tavern"));
    }

    @Test
    void unresolvedKeyPassesByDefault() {
        IsolationPolicy policy = whitelist("resource", IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT);
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decide("world", null, null));
    }
}
