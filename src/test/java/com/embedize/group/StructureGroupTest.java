package com.embedize.group;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class StructureGroupTest {

    @Test
    void independentWhitelists() {
        StructureGroup a = new StructureGroup("a", "A", List.of("pack_a"), List.of("pack_a"), List.of("world"));
        StructureGroup b = new StructureGroup("b", "B", List.of("pack_b"), List.of("pack_b"), List.of("resource"));

        Assertions.assertTrue(a.allowsWorld("world"));
        Assertions.assertFalse(a.allowsWorld("resource"));
        Assertions.assertTrue(b.allowsWorld("resource"));
        Assertions.assertFalse(b.allowsWorld("world"));
        Assertions.assertFalse(a.addNamespace("minecraft"));
        Assertions.assertTrue(a.hasPack("pack_a"));
    }
}
