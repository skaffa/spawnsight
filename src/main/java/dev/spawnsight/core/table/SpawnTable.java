package dev.spawnsight.core.table;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.spawnsight.core.rules.Category;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Vanilla spawn tables, generated at build time from the 26.3 data pack by {@link SpawnTableBuilder}
 * and shipped as {@code spawnsight/spawn_table.json}. Used on servers, where the client does not get
 * the biome spawn lists (PLAN.md §7). Plain fields for Gson.
 */
public final class SpawnTable {
	public static final String RESOURCE = "/spawnsight/spawn_table.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Minecraft version the table was made from. */
	public String version;
	/** biome id → category id → entries ({@code gameplay/natural_mob_spawns}). */
	public Map<String, Map<String, List<SpawnEntry>>> biomes = new TreeMap<>();
	/** biome id → entity id → [charge, energy budget] ({@code spawn_costs}). */
	public Map<String, Map<String, double[]>> spawnCosts = new TreeMap<>();
	/** structure id → category id → override. */
	public Map<String, Map<String, StructureOverride>> structures = new TreeMap<>();
	/** dimension type id → light rules. */
	public Map<String, DimensionRules> dimensions = new TreeMap<>();
	/** {@code gameplay/surface_slime_spawn_chance} for each of the 8 moon phases, full moon first. */
	public float[] surfaceSlimeByMoonPhase = new float[0];

	/** A structure's {@code spawn_overrides} entry. {@code boundingBox} is "piece" or "full". */
	public record StructureOverride(String boundingBox, List<SpawnEntry> spawns) {
	}

	public record DimensionRules(int blockLightLimit, int lightTestMin, int lightTestMax, float ambientLight) {
	}

	/** Entries of a biome for a category; empty when the biome or category has none. */
	public List<SpawnEntry> list(String biome, Category category) {
		Map<String, List<SpawnEntry>> byCat = biomes.get(biome);
		if (byCat == null) {
			return List.of();
		}
		List<SpawnEntry> l = byCat.get(category.id());
		return l == null ? List.of() : l;
	}

	public boolean knowsBiome(String biome) {
		return biomes.containsKey(biome);
	}

	public float surfaceSlimeBest() {
		float best = 0;
		for (float f : surfaceSlimeByMoonPhase) {
			best = Math.max(best, f);
		}
		return best;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public static SpawnTable read(Reader r) {
		SpawnTable t = GSON.fromJson(r, SpawnTable.class);
		if (t == null) {
			throw new IllegalArgumentException("empty spawn table");
		}
		return t;
	}
}
