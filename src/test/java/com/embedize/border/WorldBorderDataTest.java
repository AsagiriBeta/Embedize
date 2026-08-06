package com.embedize.border;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldBorderDataTest {

    @Test
    void squareInsideOutside() {
        WorldBorderData border = new WorldBorderData(0, 0, 100);
        Assertions.assertTrue(border.insideBorder(0, 0, WorldBorderData.Shape.SQUARE));
        Assertions.assertTrue(border.insideBorder(99, 99, WorldBorderData.Shape.SQUARE));
        Assertions.assertFalse(border.insideBorder(101, 0, WorldBorderData.Shape.SQUARE));
        Assertions.assertFalse(border.insideBorder(0, -101, WorldBorderData.Shape.SQUARE));
    }

    @Test
    void roundInsideOutside() {
        WorldBorderData border = new WorldBorderData(0, 0, 100);
        Assertions.assertTrue(border.insideBorder(0, 0, WorldBorderData.Shape.ROUND));
        Assertions.assertTrue(border.insideBorder(50, 0, WorldBorderData.Shape.ROUND));
        Assertions.assertFalse(border.insideBorder(101, 0, WorldBorderData.Shape.ROUND));
        // corner of square bounding box is outside the circle
        Assertions.assertFalse(border.insideBorder(90, 90, WorldBorderData.Shape.ROUND));
    }

    @Test
    void shapeOverride() {
        WorldBorderData border = new WorldBorderData(0, 0, 100, 100, WorldBorderData.Shape.SQUARE);
        // even if global is round, override forces square — corner inside square
        Assertions.assertTrue(border.insideBorder(90, 90, WorldBorderData.Shape.ROUND));
    }

    @Test
    void parseShape() {
        Assertions.assertEquals(WorldBorderData.Shape.ROUND, WorldBorderData.Shape.parse("circle", WorldBorderData.Shape.SQUARE));
        Assertions.assertEquals(WorldBorderData.Shape.SQUARE, WorldBorderData.Shape.parse("box", WorldBorderData.Shape.ROUND));
        Assertions.assertEquals(WorldBorderData.Shape.SQUARE, WorldBorderData.Shape.parse("nope", WorldBorderData.Shape.SQUARE));
    }
}
