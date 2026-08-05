package com.embedize.structure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class IsolationPolicyTest {

    private IsolationPolicy strictAllowlist(String allowed, Set<String> sealed, IsolationPolicy.StructureFilterMode filter) {
        return new IsolationPolicy(
                true,
                true,
                IsolationPolicy.Mode.ALLOWLIST,
                filter,
                Set.of(allowed),
                sealed,
                Set.of("nova_structures", "other_pack"),
                true
        );
    }

    @Test
    void resourceOnlyNeverLeaksToDefaultWorld() {
        IsolationPolicy policy = strictAllowlist(
                "resource",
                Set.of("world", "world_nether", "world_the_end"),
                IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT
        );

        Assertions.assertEquals(
                IsolationPolicy.Decision.ALLOW,
                policy.decide("resource", "nova_structures", "tavern")
        );
        Assertions.assertEquals(
                IsolationPolicy.Decision.DENY,
                policy.decide("world", "nova_structures", "tavern")
        );
        Assertions.assertEquals(
                IsolationPolicy.Decision.DENY,
                policy.decide("world_nether", "other_pack", "ruin")
        );
        Assertions.assertEquals(
                IsolationPolicy.Decision.DENY,
                policy.decide("somewhere_else", "nova_structures", "tavern")
        );
    }

    @Test
    void sealedWorldOverridesAccidentalAllowlist() {
        IsolationPolicy policy = new IsolationPolicy(
                true,
                true,
                IsolationPolicy.Mode.ALLOWLIST,
                IsolationPolicy.StructureFilterMode.NAMESPACES,
                Set.of("world", "resource"),
                Set.of("world"),
                Set.of("nova_structures"),
                true
        );
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("world", "nova_structures", "x"));
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, policy.decide("resource", "nova_structures", "x"));
    }

    @Test
    void vanillaStructuresPassUnlessFilterAll() {
        IsolationPolicy nonMc = strictAllowlist(
                "resource",
                Set.of("world"),
                IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT
        );
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, nonMc.decide("world", "minecraft", "village_plains"));

        IsolationPolicy all = strictAllowlist(
                "resource",
                Set.of("world"),
                IsolationPolicy.StructureFilterMode.ALL
        );
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, all.decide("world", "minecraft", "village_plains"));
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, all.decide("resource", "minecraft", "village_plains"));
    }

    @Test
    void emptyAllowlistDeniesEverywhere() {
        IsolationPolicy policy = new IsolationPolicy(
                true,
                true,
                IsolationPolicy.Mode.ALLOWLIST,
                IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT,
                Set.of(),
                Set.of("world"),
                Set.of(),
                true
        );
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("resource", "nova_structures", "x"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("world", "nova_structures", "x"));
    }

    @Test
    void unresolvedKeyDeniedInStrictMode() {
        IsolationPolicy policy = strictAllowlist(
                "resource",
                Set.of("world"),
                IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT
        );
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("world", null, null));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("resource", "  ", null));
    }
}
