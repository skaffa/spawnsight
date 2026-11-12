package dev.spawnsight.client.source;

import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.table.SpawnEntry;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Spawn lists for the candidate blocks of one 16³ section, by local index ({@code (y << 8) | (z << 4) | x}).
 * Blocks with the same lists share one array (indexed by {@link Category#ordinal()}).
 *
 * @param covered the candidate blocks these lists were made for
 * @param origin  where the lists come from, for the UI
 */
public record SectionLists(Map<Integer, List<SpawnEntry>[]> byIndex, BitSet covered, Origin origin) {
	public enum Origin {
		/** Read from the integrated server: exact, including structures and data packs. */
		SERVER,
		/** Vanilla 26.3 table by biome: structures, data packs and plugins unknown. */
		TABLE
	}

	public List<SpawnEntry> at(int index, Category c) {
		List<SpawnEntry>[] l = byIndex.get(index);
		return l == null ? List.of() : l[c.ordinal()];
	}

	public static int index(int lx, int ly, int lz) {
		return (ly << 8) | (lz << 4) | lx;
	}
}
