package com.embedize.structure;

import com.embedize.group.StructureGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class IsolationPolicyTest {

    private IsolationPolicy policy(StructureGroup group, boolean manageUngrouped) {
        return new IsolationPolicy(true, false, manageUngrouped, new IsolationPolicy.GroupWorldResolver() {
            @Override
            public Optional<StructureGroup> findGroup(String namespace) {
                return group.ownsNamespace(namespace) ? Optional.of(group) : Optional.empty();
            }

            @Override
            public boolean isWorldAllowed(StructureGroup g, String worldName) {
                return g.allowsWorld(worldName);
            }
        });
    }

    @Test
    void groupWhitelistIndependent() {
        StructureGroup dungeons = new StructureGroup("dungeons", "Dungeons",
                List.of("nova_structures"), List.of("resource"));
        IsolationPolicy policy = policy(dungeons, false);

        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, policy.decide("resource", "nova_structures", "tavern"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decide("world", "nova_structures", "tavern"));
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decide("world", "minecraft", "village_plains"));
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decide("resource", "minecraft", "stronghold"));
    }

    @Test
    void ungroupedPassesUnlessManageUngrouped() {
        StructureGroup dungeons = new StructureGroup("dungeons", "Dungeons",
                List.of("nova_structures"), List.of("resource"));
        IsolationPolicy soft = policy(dungeons, false);
        IsolationPolicy hard = policy(dungeons, true);

        Assertions.assertEquals(IsolationPolicy.Decision.PASS, soft.decide("world", "other_pack", "x"));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, hard.decide("world", "other_pack", "x"));
    }

    @Test
    void decideWithFlags() {
        IsolationPolicy policy = policy(
                new StructureGroup("g", "G", List.of("nova_structures"), List.of("resource")),
                false
        );
        Assertions.assertEquals(IsolationPolicy.Decision.ALLOW, policy.decideWithFlags("nova_structures", true, true));
        Assertions.assertEquals(IsolationPolicy.Decision.DENY, policy.decideWithFlags("nova_structures", false, true));
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decideWithFlags("minecraft", false, false));
        Assertions.assertEquals(IsolationPolicy.Decision.PASS, policy.decideWithFlags("other", false, false));
    }
}
