package com.embedize.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SoftBeardAdaptationTest {

    @Test
    void insidePieceBoxAlwaysCarves() {
        assertTrue(SoftBeardAdaptation.shouldCarve(5, 10, 5, 0, 0, 0, 10, 20, 10, 6));
    }

    @Test
    void softShellNearPieceCarves() {
        // 3 blocks outside maxX — within kernel 6
        assertTrue(SoftBeardAdaptation.shouldCarve(13, 10, 5, 0, 0, 0, 10, 20, 10, 6));
    }

    @Test
    void farOutsideHullDoesNotCarve() {
        // Far corner of a StructureStart AABB that contains no piece — must stay solid
        assertFalse(SoftBeardAdaptation.shouldCarve(40, 10, 40, 0, 0, 0, 10, 20, 10, 6));
    }

    @Test
    void zeroKernelIsHardPieceOnly() {
        assertTrue(SoftBeardAdaptation.shouldCarve(5, 10, 5, 0, 0, 0, 10, 20, 10, 0));
        assertFalse(SoftBeardAdaptation.shouldCarve(11, 10, 5, 0, 0, 0, 10, 20, 10, 0));
    }

    @Test
    void ellipsoidIsSofterThanCube() {
        // Corner of a cube radius 16 is outside the ellipsoid
        assertFalse(SoftBeardAdaptation.shouldCarveEllipsoid(16, 0, 16, 0, 0, 0, 16, 24));
        assertTrue(SoftBeardAdaptation.shouldCarveEllipsoid(8, 0, 0, 0, 0, 0, 16, 24));
    }
}
