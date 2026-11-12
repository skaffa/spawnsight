package dev.spawnsight.client.source;

import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.table.SpawnEntry;
import dev.spawnsight.core.table.SpawnTable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawn lists from the vanilla table by the biome the client sees (multiplayer, PLAN.md §7). Structure
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
		for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
			p.set(bx + (i & 15), by + (i >> 8), bz + ((i >> 4) & 15));
			out.put(i, lists(level.getBiome(p)));
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
	}
}
