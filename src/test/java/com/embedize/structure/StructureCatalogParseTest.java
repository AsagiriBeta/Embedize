package com.embedize.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog JSON shape used by {@link StructureCatalog} (no Bukkit server required).
 */
class StructureCatalogParseTest {

    @Test
    void catalogJsonShapeIsReadable() {
        JsonObject root = JsonParser.parseString("""
                {
                  "packs": ["DnT"],
                  "nbtCount": 12,
                  "jsonCount": 4,
                  "tagCount": 2,
                  "structures": [
                    {"id": "minecraft:village_plains", "type": "minecraft:jigsaw", "biomes": "#minecraft:has_structure/village_plains", "env": "OVERWORLD"},
                    {"id": "minecraft:fortress", "type": "minecraft:fortress", "biomes": "#minecraft:is_nether", "env": "NETHER"}
                  ]
                }
                """).getAsJsonObject();
        assertEquals(12, root.get("nbtCount").getAsInt());
        JsonArray structures = root.getAsJsonArray("structures");
        assertEquals(2, structures.size());
        assertEquals("minecraft:village_plains", structures.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(World.Environment.NORMAL, envOf("OVERWORLD"));
        assertEquals(World.Environment.NETHER, envOf("NETHER"));
        assertTrue(root.getAsJsonArray("packs").size() >= 1);
    }

    private static World.Environment envOf(String raw) {
        return switch (raw.trim().toUpperCase()) {
            case "NETHER" -> World.Environment.NETHER;
            case "END", "THE_END" -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
    }
}
