package dev.spawnsight.client.source;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.table.SpawnEntry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exact spawn lists from the integrated server in singleplayer / LAN host (PLAN.md §7): the same lookup
 * as {@code NaturalSpawner.mobsAt} (fortress rule, then {@code ChunkGenerator.getMobsAt} with structure
 * overrides, then {@code gameplay/natural_mob_spawns}). Runs on the server thread, one batch per client tick.
 */
public final class ServerLists {
	private static final int MAX_SECTIONS_PER_BATCH = 64;
	private static final MobCategory[] CATS = MobCategory.values();

	private final Map<Long, BitSet> requests = new LinkedHashMap<>();
	private final Map<Long, SectionLists> results = new ConcurrentHashMap<>();
	private volatile boolean pending;
	private volatile Long seed;
	private volatile float surfaceSlimeChance = Float.NaN;
	private volatile boolean mobSpawning = true;
	/** Server-thread only: converted lists by identity. */
	private final Map<WeightedList<MobSpawnSettings.SpawnerData>, List<SpawnEntry>> converted = new IdentityHashMap<>();

	public static boolean available() {
		return Minecraft.getInstance().hasSingleplayerServer();
	}

	/** World seed read from the server, or null until known. */
	public Long seed() {
		return seed;
	}

	/** {@code gameplay/surface_slime_spawn_chance} of the dimension, NaN until known. */
	public float surfaceSlimeChance() {
		return surfaceSlimeChance;
	}

	public boolean mobSpawning() {
		return mobSpawning;
	}

	/** Lists covering these candidates, or null after asking the server (ready in a later tick). */
	public SectionLists get(long section, BitSet candidates) {
		SectionLists r = results.get(section);
		if (r != null) {
			BitSet missing = (BitSet) candidates.clone();
			missing.andNot(r.covered());
			if (missing.isEmpty()) {
				return r;
			}
		}
		requests.put(section, (BitSet) candidates.clone());
		return null;
	}

	/** Lists must be read again (nether bricks below changed, dimension changed...). */
	public void forget(long section) {
		results.remove(section);
	}

	public void clear() {
		results.clear();
		requests.clear();
		seed = null;
		surfaceSlimeChance = Float.NaN;
	}

	public void tick(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.level == null || pending) {
			return;
		}
		ResourceKey<Level> dim = mc.level.dimension();
		Map<Long, BitSet> batch = new HashMap<>();
		var it = requests.entrySet().iterator();
		while (it.hasNext() && batch.size() < MAX_SECTIONS_PER_BATCH) {
			var e = it.next();
			batch.put(e.getKey(), e.getValue());
			it.remove();
		}
		pending = true;
		server.execute(() -> {
			try {
				ServerLevel level = server.getLevel(dim);
				if (level == null) {
					return;
				}
				seed = level.getSeed();
				surfaceSlimeChance = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE);
				mobSpawning = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS);
				for (var e : batch.entrySet()) {
					results.put(e.getKey(), read(level, e.getKey(), e.getValue()));
				}
			} catch (RuntimeException ex) {
				SpawnsightClient.LOG.warn("server spawn list read failed", ex);
			} finally {
				pending = false;
			}
		});
	}

	@SuppressWarnings("unchecked")
	private SectionLists read(ServerLevel level, long section, BitSet candidates) {
		StructureManager structures = level.structureManager();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		int bx = SectionPos.sectionToBlockCoord(SectionPos.x(section));
		int by = SectionPos.sectionToBlockCoord(SectionPos.y(section));
		int bz = SectionPos.sectionToBlockCoord(SectionPos.z(section));
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Map<Integer, List<SpawnEntry>[]> out = new HashMap<>();
		// blocks with the same list objects share one array
		Map<List<Object>, List<SpawnEntry>[]> shared = new HashMap<>();
		for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
			p.set(bx + (i & 15), by + (i >> 8), bz + ((i >> 4) & 15));
			if (!level.isLoaded(p)) {
				continue;
			}
			List<Object> key = new ArrayList<>(CATS.length);
			WeightedList<MobSpawnSettings.SpawnerData>[] raw = new WeightedList[CATS.length];
			for (MobCategory c : CATS) {
				WeightedList<MobSpawnSettings.SpawnerData> l = c == MobCategory.MISC ? WeightedList.of()
						: NaturalSpawner.isInNetherFortressBounds(p, level, c, structures) ? NetherFortressStructure.FORTRESS_ENEMIES
								: generator.getMobsAt(level, structures, c, p);
				raw[c.ordinal()] = l;
				key.add(new IdentityKey(l));
			}
			List<SpawnEntry>[] arr = shared.computeIfAbsent(key, k -> {
				List<SpawnEntry>[] a = new List[Category.values().length];
				for (MobCategory c : CATS) {
					a[Category.valueOf(c.name()).ordinal()] = convert(raw[c.ordinal()]);
				}
				return a;
			});
			out.put(i, arr);
		}
		return new SectionLists(out, candidates, SectionLists.Origin.SERVER);
	}

	private List<SpawnEntry> convert(WeightedList<MobSpawnSettings.SpawnerData> l) {
		if (converted.size() > 4096) {
			converted.clear(); // in case lists are built per lookup (data packs): keep the cache bounded
		}
		return converted.computeIfAbsent(l, k -> {
			List<SpawnEntry> out = new ArrayList<>();
			for (Weighted<MobSpawnSettings.SpawnerData> w : k.unwrap()) {
				MobSpawnSettings.SpawnerData d = w.value();
				String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(d.type()).toString();
				out.add(new SpawnEntry(id, w.weight(), d.count().minInclusive(), d.count().maxInclusive()));
			}
			return List.copyOf(out);
		});
	}

	/** The exact lists at one block, on the server thread (self-tests). Indexed by {@link Category#ordinal()}. */
	@SuppressWarnings("unchecked")
	public static List<SpawnEntry>[] listsAt(ServerLevel level, BlockPos p) {
		StructureManager structures = level.structureManager();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		List<SpawnEntry>[] a = new List[Category.values().length];
		for (MobCategory c : CATS) {
			WeightedList<MobSpawnSettings.SpawnerData> l = c == MobCategory.MISC ? WeightedList.of()
					: NaturalSpawner.isInNetherFortressBounds(p, level, c, structures) ? NetherFortressStructure.FORTRESS_ENEMIES
							: generator.getMobsAt(level, structures, c, p);
			List<SpawnEntry> out = new ArrayList<>();
			for (Weighted<MobSpawnSettings.SpawnerData> w : l.unwrap()) {
				MobSpawnSettings.SpawnerData d = w.value();
				out.add(new SpawnEntry(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(d.type()).toString(), w.weight(),
						d.count().minInclusive(), d.count().maxInclusive()));
			}
			a[Category.valueOf(c.name()).ordinal()] = out;
		}
		return a;
	}

	/** Identity wrapper so equal-content lists from different sources are still told apart. */
	private record IdentityKey(Object o) {
		@Override
		public boolean equals(Object other) {
			return other instanceof IdentityKey k && k.o == o;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(o);
		}
	}
}
