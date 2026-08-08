package com.embedize.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void densityFactorIsOneInsideAndFallsOff() {
        assertEquals(1.0, SoftBeardAdaptation.densityFactor(5, 10, 5, 0, 0, 0, 10, 20, 10, 6));
        double near = SoftBeardAdaptation.densityFactor(13, 10, 5, 0, 0, 0, 10, 20, 10, 6);
        assertTrue(near > 0.0 && near < 1.0);
        assertEquals(0.0, SoftBeardAdaptation.densityFactor(40, 10, 40, 0, 0, 0, 10, 20, 10, 6));
    }

    @Test
    void densityClearUsesThreshold() {
        // Inside always clears
        assertTrue(SoftBeardAdaptation.shouldClearByDensity(5, 10, 5, 0, 0, 0, 10, 20, 10, 6));
        // Far shell edge: factor near 0 — below threshold
        assertFalse(SoftBeardAdaptation.shouldClearByDensity(16, 10, 5, 0, 0, 0, 10, 20, 10, 6));
        // Mid shell (dist 3 of kernel 6 → factor 0.5) clears at threshold 0.35
        assertTrue(SoftBeardAdaptation.shouldClearByDensity(13, 10, 5, 0, 0, 0, 10, 20, 10, 6));
    }

    @Test
    void influencesColumnSkipsFarXz() {
        assertTrue(SoftBeardAdaptation.influencesColumn(5, 5, 0, 0, 10, 10, 6));
        assertTrue(SoftBeardAdaptation.influencesColumn(13, 5, 0, 0, 10, 10, 6));
        assertFalse(SoftBeardAdaptation.influencesColumn(40, 40, 0, 0, 10, 10, 6));
    }
}
