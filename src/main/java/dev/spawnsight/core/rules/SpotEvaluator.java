package dev.spawnsight.core.rules;

import dev.spawnsight.core.table.SpawnEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Combines the verdicts of every mob in the spawn lists at one block into the overlay colour.
 * Each distinct entity type is evaluated once even when it appears in several entries.
 */
public final class SpotEvaluator {
	private SpotEvaluator() {
	}

	/** Overlay colour of a block. */
	public enum Shade {
		/** No mob in the lists can spawn here. */
		NONE,
		/** A passive, ambient or water mob can spawn here (and no hostile one). */
		PASSIVE,
		/** A hostile mob can spawn only if an unknown fact is true (slime chunk without the seed). */
		UNKNOWN,
		/** A hostile mob can spawn here at night, in a thunderstorm or in another moon phase. */
		HOSTILE_LATER,
		/** A hostile mob can spawn here now. */
		HOSTILE_NOW
	}

	/**
	 * Result for one block.
	 *
	 * @param hostile verdicts of the MONSTER list
	 * @param passive verdicts of the other lists
	 */
	public record Spot(Shade shade, List<Verdict> hostile, List<Verdict> passive) {
		public boolean passivePossible() {
			for (Verdict v : passive) {
				if (v.possibleSometime()) {
					return true;
				}
			}
			return false;
		}
	}

	/** Where the lists come from, per category. */
	public interface Lists {
		List<SpawnEntry> at(int x, int y, int z, Category category);
	}

	/**
	 * @param filter if not null, only this entity id counts (farm mode)
	 * @param specs  entity id → spec; null for ids the client does not know (skipped)
	 */
	public static Spot evaluate(World w, Env e, Lists lists, Function<String, MobSpec> specs, boolean withPassive, String filter, int x, int y, int z) {
		List<Verdict> hostile = run(w, e, lists.at(x, y, z, Category.MONSTER), specs, filter, x, y, z);
		List<Verdict> passive = new ArrayList<>();
		if (withPassive) {
			for (Category c : Category.values()) {
				if (c != Category.MONSTER && c != Category.MISC) {
					passive.addAll(run(w, e, lists.at(x, y, z, c), specs, filter, x, y, z));
				}
			}
		}
		return new Spot(shade(hostile, passive), hostile, passive);
	}

	/**
	 * Only the shade, as fast as possible for the scanner: no text, and it stops as soon as the answer
	 * cannot change (a hostile mob that can spawn now). Same result as {@code evaluate(...).shade()}.
	 */
	public static Shade shade(World w, Env e, Lists lists, Function<String, MobSpec> specs, boolean withPassive, String filter, int x, int y, int z) {
		Shade best = Shade.NONE;
		List<SpawnEntry> monsters = lists.at(x, y, z, Category.MONSTER);
		for (int i = 0; i < monsters.size(); i++) {
			SpawnEntry entry = monsters.get(i);
			if ((filter != null && !filter.equals(entry.type())) || seenBefore(monsters, i)) {
				continue;
			}
			MobSpec spec = specs.apply(entry.type());
			if (spec == null) {
				continue;
			}
			Shade h = switch (RuleEngine.status(w, e, spec, x, y, z).status()) {
				case NOW -> Shade.HOSTILE_NOW;
				case LATER -> Shade.HOSTILE_LATER;
				case UNKNOWN -> Shade.UNKNOWN;
				case NEVER -> Shade.NONE;
			};
			if (h == Shade.HOSTILE_NOW) {
				return h;
			}
			if (h.ordinal() > best.ordinal()) {
				best = h;
			}
		}
		if (best != Shade.NONE || !withPassive) {
			return best;
		}
		for (Category c : Category.values()) {
			if (c == Category.MONSTER || c == Category.MISC) {
				continue;
			}
			List<SpawnEntry> l = lists.at(x, y, z, c);
			for (int i = 0; i < l.size(); i++) {
				SpawnEntry entry = l.get(i);
				if ((filter != null && !filter.equals(entry.type())) || seenBefore(l, i)) {
					continue;
				}
				MobSpec spec = specs.apply(entry.type());
				if (spec != null && RuleEngine.status(w, e, spec, x, y, z).possibleSometime()) {
					return Shade.PASSIVE;
				}
			}
		}
		return Shade.NONE;
	}

	private static boolean seenBefore(List<SpawnEntry> l, int i) {
		String t = l.get(i).type();
		for (int j = 0; j < i; j++) {
			if (l.get(j).type().equals(t)) {
				return true;
			}
		}
		return false;
	}

	private static List<Verdict> run(World w, Env e, List<SpawnEntry> list, Function<String, MobSpec> specs, String filter, int x, int y, int z) {
		List<Verdict> out = new ArrayList<>(list.size());
		Set<String> seen = new HashSet<>();
		for (SpawnEntry entry : list) {
			if ((filter != null && !filter.equals(entry.type())) || !seen.add(entry.type())) {
				continue;
			}
			MobSpec spec = specs.apply(entry.type());
			if (spec != null) {
				out.add(RuleEngine.evaluate(w, e, spec, x, y, z));
			}
		}
		return out;
	}

	static Shade shade(List<Verdict> hostile, List<Verdict> passive) {
		Shade s = Shade.NONE;
		for (Verdict v : hostile) {
			Shade h = switch (v.status()) {
				case NOW -> Shade.HOSTILE_NOW;
				case LATER -> Shade.HOSTILE_LATER;
				case UNKNOWN -> Shade.UNKNOWN;
				case NEVER -> Shade.NONE;
			};
			if (h.ordinal() > s.ordinal()) {
				s = h;
			}
		}
		if (s == Shade.NONE) {
			for (Verdict v : passive) {
				if (v.possibleSometime()) {
					return Shade.PASSIVE;
				}
			}
		}
		return s;
	}
}
