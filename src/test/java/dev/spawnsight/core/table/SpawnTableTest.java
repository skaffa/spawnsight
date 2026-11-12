package dev.spawnsight.core.table;

import static org.junit.jupiter.api.Assertions.*;

import dev.spawnsight.core.rules.Category;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class SpawnTableTest {
	private static SpawnTable vanilla() throws Exception {
		var in = SpawnTable.class.getResourceAsStream(SpawnTable.RESOURCE);
		assertNotNull(in, "generated spawn table missing from resources");
		try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return SpawnTable.read(r);
		}
	}

	@Test
	void builderReadsOverlayAttributeAndCounts() {
		String biome = """
				{"attributes": {"minecraft:gameplay/natural_mob_spawns": {"modifier": "overlay", "argument": {
				  "spawn_costs": {"minecraft:ghast": {"charge": 0.7, "energy_budget": 0.15}},
				  "spawns_by_category": {"monster": [
				    {"type": "minecraft:zombie", "count": 4, "weight": 95},
				    {"type": "minecraft:blaze", "count": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 3}, "weight": 10}]}}}}}
				""";
		String dim = """
				{"monster_spawn_block_light_limit": 0, "ambient_light": 0.0,
				 "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7}}
				""";
		String hut = """
				{"spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": [{"type": "minecraft:witch", "count": 1, "weight": 1}]}}}
				""";
		SpawnTable t = SpawnTableBuilder.build(Map.of(
				"data/minecraft/worldgen/biome/test.json", biome,
				"data/minecraft/dimension_type/overworld.json", dim,
				"data/minecraft/worldgen/structure/swamp_hut.json", hut), "test");
		List<SpawnEntry> m = t.list("minecraft:test", Category.MONSTER);
		assertEquals(List.of(new SpawnEntry("minecraft:zombie", 95, 4, 4), new SpawnEntry("minecraft:blaze", 10, 2, 3)), m);
		assertEquals(List.of(), t.list("minecraft:test", Category.CREATURE));
		assertArrayEquals(new double[] {0.7, 0.15}, t.spawnCosts.get("minecraft:test").get("minecraft:ghast"));
		assertEquals(new SpawnTable.DimensionRules(0, 0, 7, 0.0F), t.dimensions.get("minecraft:overworld"));
		assertEquals("piece", t.structures.get("minecraft:swamp_hut").get("monster").boundingBox());
	}

	@Test
	void vanillaTableMatchesThe263Data() throws Exception {
		SpawnTable t = vanilla();
		assertEquals("26.3", t.version);
		assertTrue(t.biomes.size() >= 60, "biomes: " + t.biomes.size());
		assertTrue(t.list("minecraft:swamp", Category.MONSTER).stream().anyMatch(e -> e.type().equals("minecraft:slime")));
		assertEquals(List.of(), t.list("minecraft:mushroom_fields", Category.MONSTER), "mushroom fields: no monsters");
		assertEquals(List.of(), t.list("minecraft:deep_dark", Category.MONSTER), "deep dark: no monsters");
		assertTrue(t.knowsBiome("minecraft:deep_dark"));
		assertEquals(new SpawnTable.DimensionRules(15, 7, 7, 0.1F), t.dimensions.get("minecraft:the_nether"));
		assertEquals(new SpawnTable.DimensionRules(0, 15, 15, 0.25F), t.dimensions.get("minecraft:the_end"));
		assertArrayEquals(new float[] {0.5F, 0.375F, 0.25F, 0.125F, 0.0F, 0.125F, 0.25F, 0.375F}, t.surfaceSlimeByMoonPhase);
		assertEquals(0.5F, t.surfaceSlimeBest());
		var fortress = t.structures.get("minecraft:fortress").get("monster");
		assertEquals("piece", fortress.boundingBox());
		assertEquals(5, fortress.spawns().size());
		assertEquals("full", t.structures.get("minecraft:monument").get("monster").boundingBox());
		assertTrue(t.structures.get("minecraft:ancient_city").get("monster").spawns().isEmpty());
		assertTrue(t.spawnCosts.containsKey("minecraft:soul_sand_valley"));
	}
}
