package com.embedize.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherTerrainGeneratorTest {

    @Test
    void interpolatesDensityGridWithoutChunkLocalDiscontinuities() {
        double[][][] grid = new double[2][2][2];
        grid[1][0][0] = 4.0;
        grid[0][1][0] = 4.0;
        grid[0][0][1] = 4.0;
        grid[1][1][0] = 8.0;
        grid[1][0][1] = 8.0;
        grid[0][1][1] = 8.0;
        grid[1][1][1] = 12.0;

        assertEquals(0.0, NetherTerrainGenerator.interpolateCheese(grid, 0, 0, 0));
        assertEquals(6.0, NetherTerrainGenerator.interpolateCheese(grid, 2, 2, 2));
        assertEquals(9.0, NetherTerrainGenerator.interpolateCheese(grid, 3, 3, 3));
    }
}
