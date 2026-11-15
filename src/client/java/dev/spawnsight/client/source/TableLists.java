package dev.spawnsight.client.source;

import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.table.SpawnEntry;
import dev.spawnsight.core.table.SpawnTable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawn lists from the vanilla table by the biome the client sees (multiplayer). Structure
 * spawns are not known here.
 */
public final class TableLists {
	private final SpawnTable table;
	private final Map<Holder<Biome>, List<SpawnEntry>[]> byBiome = new IdentityHashMap<>();

	public TableLists(SpawnTable table) {
		this.table = table;
	}

	public SpawnTable table() {
		return table;
	}

	public SectionLists get(Level level, long section, BitSet candidates) {
		int bx = SectionPos.sectionToBlockCoord(SectionPos.x(section));
		int by = SectionPos.sectionToBlockCoord(SectionPos.y(section));
		int bz = SectionPos.sectionToBlockCoord(SectionPos.z(section));
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Map<Integer, List<SpawnEntry>[]> out = new HashMap<>();
		boolean nether = level.dimension() == Level.NETHER;
		for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
			p.set(bx + (i & 15), by + (i >> 8), bz + ((i >> 4) & 15));
			List<SpawnEntry>[] l = lists(level.getBiome(p));
			if (nether && fortressAssumed(level, p)) {
				l = fortress(l);
			}
			out.put(i, l);
		}
		return new SectionLists(out, (BitSet) candidates.clone(), SectionLists.Origin.TABLE);
	}

	@SuppressWarnings("unchecked")
	private List<SpawnEntry>[] lists(Holder<Biome> biome) {
		return byBiome.computeIfAbsent(biome, b -> {
			String id = b.unwrapKey().map(k -> k.identifier().toString()).orElse("");
			List<SpawnEntry>[] arr = new List[Category.values().length];
			for (Category c : Category.values()) {
				arr[c.ordinal()] = table.list(id, c);
			}
			return arr;
		});
	}

	public void clear() {
		byBiome.clear();
		fortressByBiome.clear();
	}

	/**
	 * The fortress rule without structure data: vanilla uses the fortress list for monsters standing on
	 * nether bricks inside a fortress; on a server Spawnsight assumes nether bricks in the nether are one.
	 */
	public static boolean fortressAssumed(Level level, BlockPos spawnPos) {
		return level.dimension() == Level.NETHER && level.getBlockState(spawnPos.below()).is(Blocks.NETHER_BRICKS);
	}

	private final Map<List<SpawnEntry>[], List<SpawnEntry>[]> fortressByBiome = new IdentityHashMap<>();

	@SuppressWarnings("unchecked")
	private List<SpawnEntry>[] fortress(List<SpawnEntry>[] biome) {
		return fortressByBiome.computeIfAbsent(biome, b -> {
			List<SpawnEntry>[] arr = b.clone();
			var override = table.structures.getOrDefault("minecraft:fortress", Map.of()).get(Category.MONSTER.id());
			if (override != null) {
				arr[Category.MONSTER.ordinal()] = override.spawns();
			}
			return arr;
		});
	}
}
