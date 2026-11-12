package dev.spawnsight.core.table;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds a {@link SpawnTable} from the vanilla data pack files ({@code data/minecraft/...}). Run at build
 * time by the {@code genSpawnTable} Gradle task on the 26.3 game jar:
 * {@code SpawnTableBuilder <minecraft jar> <output json> <version>}.
 */
public final class SpawnTableBuilder {
	private static final String BIOME = "data/minecraft/worldgen/biome/";
	private static final String STRUCTURE = "data/minecraft/worldgen/structure/";
	private static final String DIMENSION = "data/minecraft/dimension_type/";
	private static final String MOON = "data/minecraft/timeline/moon.json";
	private static final String SPAWNS = "minecraft:gameplay/natural_mob_spawns";
	private static final String SLIME = "minecraft:gameplay/surface_slime_spawn_chance";
	/** One moon phase lasts one day in the 26.3 moon timeline (period 192000 = 8 phases). */
	private static final int PHASE_TICKS = 24000;

	private SpawnTableBuilder() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			throw new IllegalArgumentException("usage: SpawnTableBuilder <minecraft jar> <output json> <version>");
		}
		Map<String, String> files = new TreeMap<>();
		try (ZipFile zip = new ZipFile(args[0])) {
			Enumeration<? extends ZipEntry> en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				String n = e.getName();
				if (!e.isDirectory() && n.endsWith(".json") && (n.startsWith(BIOME) || n.startsWith(STRUCTURE) || n.startsWith(DIMENSION) || n.equals(MOON))) {
					try (InputStream in = zip.getInputStream(e)) {
						files.put(n, new String(in.readAllBytes(), StandardCharsets.UTF_8));
					}
				}
			}
		}
		SpawnTable t = build(files, args[2]);
		if (t.biomes.isEmpty() || t.dimensions.isEmpty()) {
			throw new IllegalStateException("no biome or dimension data found in " + args[0]);
		}
		Path out = Path.of(args[1]);
		Files.createDirectories(out.getParent());
		Files.writeString(out, t.toJson(), StandardCharsets.UTF_8);
		System.out.println("spawn table: " + t.biomes.size() + " biomes, " + t.structures.size() + " structures with spawn overrides -> " + out);
	}

	/** Builds the table from data pack paths and their JSON text. */
	public static SpawnTable build(Map<String, String> files, String version) {
		SpawnTable t = new SpawnTable();
		t.version = version;
		for (Map.Entry<String, String> f : files.entrySet()) {
			String path = f.getKey();
			JsonObject json = JsonParser.parseString(f.getValue()).getAsJsonObject();
			if (path.startsWith(BIOME)) {
				biome(t, id(path, BIOME), json);
			} else if (path.startsWith(STRUCTURE)) {
				structure(t, id(path, STRUCTURE), json);
			} else if (path.startsWith(DIMENSION)) {
				dimension(t, id(path, DIMENSION), json);
			} else if (path.equals(MOON)) {
				moon(t, json);
			}
		}
		return t;
	}

	private static String id(String path, String prefix) {
		return "minecraft:" + path.substring(prefix.length(), path.length() - ".json".length());
	}

	private static void biome(SpawnTable t, String id, JsonObject json) {
		Map<String, List<SpawnEntry>> byCat = new TreeMap<>();
		JsonObject attrs = json.has("attributes") ? json.getAsJsonObject("attributes") : new JsonObject();
		JsonElement spawns = attrs.get(SPAWNS);
		if (spawns != null && spawns.isJsonObject()) {
			JsonObject s = spawns.getAsJsonObject();
			// an attribute is either a plain value or {"modifier": ..., "argument": value}; the vanilla
			// modifier is "overlay" on the empty dimension default, which equals the argument
			if (s.has("modifier")) {
				String mod = s.get("modifier").getAsString();
				if (!mod.equals("overlay") && !mod.equals("minecraft:overlay")) {
					throw new IllegalStateException(id + ": unexpected modifier " + mod);
				}
				s = s.getAsJsonObject("argument");
			}
			if (s.has("spawns_by_category")) {
				for (Map.Entry<String, JsonElement> c : s.getAsJsonObject("spawns_by_category").entrySet()) {
					byCat.put(c.getKey(), entries(c.getValue().getAsJsonArray()));
				}
			}
			if (s.has("spawn_costs")) {
				Map<String, double[]> costs = new TreeMap<>();
				for (Map.Entry<String, JsonElement> c : s.getAsJsonObject("spawn_costs").entrySet()) {
					JsonObject o = c.getValue().getAsJsonObject();
					costs.put(c.getKey(), new double[] {o.get("charge").getAsDouble(), o.get("energy_budget").getAsDouble()});
				}
				if (!costs.isEmpty()) {
					t.spawnCosts.put(id, costs);
				}
			}
		}
		t.biomes.put(id, byCat);
	}

	private static void structure(SpawnTable t, String id, JsonObject json) {
		if (!json.has("spawn_overrides")) {
			return;
		}
		Map<String, SpawnTable.StructureOverride> byCat = new TreeMap<>();
		for (Map.Entry<String, JsonElement> c : json.getAsJsonObject("spawn_overrides").entrySet()) {
			JsonObject o = c.getValue().getAsJsonObject();
			byCat.put(c.getKey(), new SpawnTable.StructureOverride(o.get("bounding_box").getAsString(), entries(o.getAsJsonArray("spawns"))));
		}
		if (!byCat.isEmpty()) {
			t.structures.put(id, byCat);
		}
	}

	private static void dimension(SpawnTable t, String id, JsonObject json) {
		int limit = json.get("monster_spawn_block_light_limit").getAsInt();
		JsonElement test = json.get("monster_spawn_light_level");
		int min;
		int max;
		if (test.isJsonPrimitive()) {
			min = max = test.getAsInt();
		} else {
			JsonObject o = test.getAsJsonObject();
			String type = o.get("type").getAsString();
			if (!type.equals("minecraft:uniform")) {
				throw new IllegalStateException(id + ": unexpected light test " + type);
			}
			min = o.get("min_inclusive").getAsInt();
			max = o.get("max_inclusive").getAsInt();
		}
		t.dimensions.put(id, new SpawnTable.DimensionRules(limit, min, max, json.get("ambient_light").getAsFloat()));
	}

	private static void moon(SpawnTable t, JsonObject json) {
		JsonObject track = json.getAsJsonObject("tracks").getAsJsonObject(SLIME);
		if (track == null) {
			return;
		}
		int period = json.get("period_ticks").getAsInt();
		float[] phases = new float[period / PHASE_TICKS];
		Map<Integer, Float> keys = new LinkedHashMap<>();
		for (JsonElement k : track.getAsJsonArray("keyframes")) {
			JsonObject o = k.getAsJsonObject();
			keys.put(o.get("ticks").getAsInt(), o.get("value").getAsFloat());
		}
		for (int i = 0; i < phases.length; i++) {
			Float v = keys.get(i * PHASE_TICKS);
			if (v == null) {
				throw new IllegalStateException("moon timeline has no keyframe at phase " + i);
			}
			phases[i] = v;
		}
		t.surfaceSlimeByMoonPhase = phases;
	}

	private static List<SpawnEntry> entries(JsonArray arr) {
		List<SpawnEntry> out = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			JsonObject o = el.getAsJsonObject();
			int min;
			int max;
			JsonElement count = o.get("count");
			if (count == null) {
				min = max = 1;
			} else if (count.isJsonPrimitive()) {
				min = max = count.getAsInt();
			} else {
				JsonObject c = count.getAsJsonObject();
				min = c.get("min_inclusive").getAsInt();
				max = c.get("max_inclusive").getAsInt();
			}
			out.add(new SpawnEntry(o.get("type").getAsString(), o.get("weight").getAsInt(), min, max));
		}
		return out;
	}
}
