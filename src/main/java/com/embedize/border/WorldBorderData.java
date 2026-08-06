package com.embedize.border;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Invisible plugin world border (inspired by PryPurity/WorldBorder &amp; E-WorldBorder).
 * Not the vanilla blue border — players outside are teleported back inside.
 */
public final class WorldBorderData {

    public enum Shape {
        SQUARE,
        ROUND;

        public static Shape parse(String raw, Shape fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "round", "circle", "elliptic", "ellipse" -> ROUND;
                case "square", "box", "rect", "rectangle" -> SQUARE;
                default -> fallback;
            };
        }
    }

    private double x;
    private double z;
    private int radiusX;
    private int radiusZ;
    private Shape shapeOverride; // null = use global default

    private double maxX;
    private double minX;
    private double maxZ;
    private double minZ;
    private double radiusXSquared;
    private double radiusZSquared;
    private double definiteRectangleX;
    private double definiteRectangleZ;
    private double radiusSquaredQuotient;

    public WorldBorderData(double x, double z, int radiusX, int radiusZ, Shape shapeOverride) {
        setData(x, z, radiusX, radiusZ, shapeOverride);
    }

    public WorldBorderData(double x, double z, int radius) {
        this(x, z, radius, radius, null);
    }

    public void setData(double x, double z, int radiusX, int radiusZ, Shape shapeOverride) {
        this.shapeOverride = shapeOverride;
        this.radiusZ = Math.max(1, radiusZ);
        this.radiusX = Math.max(1, radiusX);
        this.x = x;
        this.z = z;
        recompute();
    }

    private void recompute() {
        maxX = x + radiusX;
        minX = x - radiusX;
        maxZ = z + radiusZ;
        minZ = z - radiusZ;
        radiusXSquared = (double) radiusX * radiusX;
        radiusZSquared = (double) radiusZ * radiusZ;
        radiusSquaredQuotient = radiusZSquared == 0.0 ? 0.0 : radiusXSquared / radiusZSquared;
        definiteRectangleX = Math.sqrt(0.5 * radiusXSquared);
        definiteRectangleZ = Math.sqrt(0.5 * radiusZSquared);
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public int getRadiusX() {
        return radiusX;
    }

    public int getRadiusZ() {
        return radiusZ;
    }

    public Shape getShapeOverride() {
        return shapeOverride;
    }

    public void setShapeOverride(Shape shapeOverride) {
        this.shapeOverride = shapeOverride;
    }

    public boolean insideBorder(double xLoc, double zLoc, Shape globalShape) {
        Shape shape = shapeOverride != null ? shapeOverride : globalShape;
        if (shape == Shape.SQUARE) {
            return !(xLoc < minX || xLoc > maxX || zLoc < minZ || zLoc > maxZ);
        }
        double xDist = Math.abs(x - xLoc);
        double zDist = Math.abs(z - zLoc);
        if (xDist < definiteRectangleX && zDist < definiteRectangleZ) {
            return true;
        }
        if (xDist >= radiusX || zDist >= radiusZ) {
            return false;
        }
        return xDist * xDist + zDist * zDist * radiusSquaredQuotient < radiusXSquared;
    }

    public boolean insideBorder(Location loc, Shape globalShape) {
        return insideBorder(loc.getX(), loc.getZ(), globalShape);
    }

    /**
     * Compute a safe location just inside the border (knockback distance from the edge).
     */
    public Location correctedPosition(Location loc, Shape globalShape, double knockBack, boolean flying) {
        Shape shape = shapeOverride != null ? shapeOverride : globalShape;
        double xLoc = loc.getX();
        double zLoc = loc.getZ();
        double kb = Math.max(0.5, knockBack);

        if (shape == Shape.SQUARE) {
            if (xLoc <= minX) {
                xLoc = minX + kb;
            } else if (xLoc >= maxX) {
                xLoc = maxX - kb;
            }
            if (zLoc <= minZ) {
                zLoc = minZ + kb;
            } else if (zLoc >= maxZ) {
                zLoc = maxZ - kb;
            }
        } else {
            double dX = xLoc - x;
            double dZ = zLoc - z;
            double dU = Math.sqrt(dX * dX + dZ * dZ);
            double dT = Math.sqrt(dX * dX / radiusXSquared + dZ * dZ / radiusZSquared);
            if (dU < 1.0E-6 || dT < 1.0E-6) {
                xLoc = x + kb;
                zLoc = z;
            } else {
                double f = (1.0 / dT) - (kb / dU);
                xLoc = x + dX * f;
                zLoc = z + dZ * f;
            }
        }

        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        int ix = Location.locToBlock(xLoc);
        int iz = Location.locToBlock(zLoc);
        double safeY = findSafeY(world, ix, Location.locToBlock(loc.getY()), iz, flying);
        if (safeY < 0) {
            return null;
        }
        return new Location(world, Math.floor(xLoc) + 0.5, safeY, Math.floor(zLoc) + 0.5, loc.getYaw(), loc.getPitch());
    }

    private static double findSafeY(World world, int x, int yStart, int z, boolean flying) {
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        int limBot = world.getMinHeight();
        int limTop = nether ? Math.min(world.getMaxHeight(), 125) : world.getMaxHeight() - 2;
        int highest = Math.min(world.getHighestBlockYAt(x, z) + 1, limTop);

        int y = yStart;
        if (flying && y > limTop && !nether) {
            return y;
        }
        if (y > limTop) {
            y = nether ? limTop : (flying ? limTop : highest);
        }
        if (y < limBot) {
            y = limBot;
        }
        if (!nether && !flying) {
            limTop = highest;
        }

        int y1 = y;
        int y2 = y;
        while (y1 > limBot || y2 < limTop) {
            if (y1 > limBot && isSafeSpot(world, x, y1, z, flying)) {
                return y1;
            }
            if (y2 < limTop && y2 != y1 && isSafeSpot(world, x, y2, z, flying)) {
                return y2;
            }
            y1--;
            y2++;
        }
        return -1;
    }

    private static final class PainfulBlocks {
        static final EnumSet<Material> SET = EnumSet.of(
                Material.LAVA,
                Material.FIRE,
                Material.SOUL_FIRE,
                Material.CACTUS,
                Material.MAGMA_BLOCK,
                Material.END_PORTAL,
                Material.POWDER_SNOW,
                Material.WITHER_ROSE,
                Material.SWEET_BERRY_BUSH
        );
    }

    private static boolean isSafeSpot(World world, int x, int y, int z, boolean flying) {
        boolean open = isSafeOpen(world.getBlockAt(x, y, z)) && isSafeOpen(world.getBlockAt(x, y + 1, z));
        if (!open || flying) {
            return open;
        }
        Block below = world.getBlockAt(x, y - 1, z);
        return (!below.isPassable() || below.getType() == Material.WATER) && !PainfulBlocks.SET.contains(below.getType());
    }

    private static boolean isSafeOpen(Block block) {
        return block.isPassable() && !PainfulBlocks.SET.contains(block.getType());
    }

    @Override
    public String toString() {
        String r = radiusX == radiusZ ? String.valueOf(radiusX) : radiusX + "x" + radiusZ;
        String shape = shapeOverride == null ? "" : " shape=" + shapeOverride.name().toLowerCase(Locale.ROOT);
        return "radius " + r + " @ " + format(x) + "," + format(z) + shape;
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
