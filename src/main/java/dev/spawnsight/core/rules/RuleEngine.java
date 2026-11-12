package dev.spawnsight.core.rules;

import dev.spawnsight.core.rules.Env.Scenario;
import dev.spawnsight.core.rules.Verdict.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Evaluates the 26.3 natural-spawn checks for one mob on one block (PLAN.md §1–§4), in vanilla order:
 * category, placement type, peaceful, spawn predicate, spawn box, walk target, spawn obstruction.
 * Player distance, mob caps and the spawn list are handled by the caller.
 * <p>
 * Checks that never change give a fixed "no". Checks that depend on light or a random draw become
 * chance factors, evaluated for every {@link Scenario} of the {@link Env}.
 */
public final class RuleEngine {
	public static final String GRASS = "minecraft:grass_block";
	public static final String SAND = "#minecraft:sand";
	public static final String LEAVES = "#minecraft:leaves";
	/** Special tag the client resolves with {@code InfestedBlock.isCompatibleHostBlock}. */
	public static final String INFESTABLE = "#spawnsight:infestable";

	private RuleEngine() {
	}

	/** Full evaluation with reasons and notes (the "why" panel). */
	public static Verdict evaluate(World w, Env e, MobSpec m, int x, int y, int z) {
		return new Run(w, e, m, MobRules.get(m.id()), x, y, z, true).run();
	}

	public static Verdict evaluate(World w, Env e, MobSpec m, MobRule r, int x, int y, int z) {
		return new Run(w, e, m, r, x, y, z, true).run();
	}

	/**
	 * Fast evaluation for the scanner: same status and chances, but {@link Verdict#reason()} is null and
	 * there are no notes, so no text is built.
	 */
	public static Verdict status(World w, Env e, MobSpec m, int x, int y, int z) {
		return new Run(w, e, m, MobRules.get(m.id()), x, y, z, false).run();
	}

	private static final Never FAST_NEVER = new Never("never");

	private record Factor(ToDoubleFunction<Scenario> p, Function<Scenario, String> whyZero) {
	}

	/** Thrown to stop at the first fixed "no". */
	private static final class Never extends RuntimeException {
		Never(String reason) {
			super(reason, null, false, false);
		}
	}

	private static final class Run {
		final World w;
		final Env e;
		final MobSpec m;
		final MobRule r;
		final int x, y, z;
		final boolean explain;
		final List<Factor> factors = new ArrayList<>();
		final List<String> notes = new ArrayList<>();
		/** Set when a factor depends on unknown data; the message says what. */
		String unknown;

		Run(World w, Env e, MobSpec m, MobRule r, int x, int y, int z, boolean explain) {
			this.explain = explain;
			this.w = w;
			this.e = e;
			this.m = m;
			this.r = r;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		Verdict run() {
			try {
				gates();
			} catch (Never n) {
				return new Verdict(m, Status.NEVER, 0, 0, null, n.getMessage(), List.copyOf(notes));
			}
			double now = chance(e.now());
			double best = now;
			String when = "now";
			for (Scenario s : e.later()) {
				double p = chance(s);
				if (p > best) {
					best = p;
					when = s.name();
				}
			}
			if (best > 0) {
				Status st = now > 0 ? Status.NOW : Status.LATER;
				String summary = !explain ? null
						: now > 0 ? "can spawn now (" + pct(now) + " per attempt)" : "can spawn " + when + " (" + pct(best) + " per attempt)";
				if (unknown != null) {
					note(unknown);
				}
				return new Verdict(m, st, now, best, when, summary, List.copyOf(notes));
			}
			if (unknown != null) {
				return new Verdict(m, Status.UNKNOWN, 0, 0, null, unknown, List.copyOf(notes));
			}
			return new Verdict(m, Status.NEVER, 0, 0, null, explain ? zeroReason() : null, List.copyOf(notes));
		}

		double chance(Scenario s) {
			double p = 1;
			for (Factor f : factors) {
				p *= f.p.applyAsDouble(s);
				if (p <= 0) {
					return 0;
				}
			}
			return p;
		}

		/**
		 * Why the product is zero. If one check fails in every situation, that check's reason (for the
		 * current situation); otherwise the different checks that fail in different situations.
		 */
		String zeroReason() {
			java.util.Map<Integer, String> byFactor = new java.util.LinkedHashMap<>();
			for (Scenario s : e.all()) {
				for (int i = 0; i < factors.size(); i++) {
					Factor f = factors.get(i);
					if (f.p.applyAsDouble(s) <= 0) {
						byFactor.putIfAbsent(i, f.whyZero.apply(s));
						break;
					}
				}
			}
			if (byFactor.size() > 1) {
				return "never all at once: " + String.join("; ", byFactor.values());
			}
			return byFactor.isEmpty() ? "never" : byFactor.values().iterator().next();
		}

		void never(Supplier<String> reason) {
			throw explain ? new Never(reason.get()) : FAST_NEVER;
		}

		void note(String what) {
			if (explain) {
				notes.add(what);
			}
		}

		void factor(ToDoubleFunction<Scenario> p, Function<Scenario, String> whyZero) {
			factors.add(new Factor(p, whyZero));
		}

		void constant(double p, String what) {
			note(what);
			factor(s -> p, s -> what);
		}

		String name(int dy) {
			return w.name(x, y + dy, z);
		}

		int sky() {
			return w.skyLight(x, y, z);
		}

		int blk() {
			return w.blockLight(x, y, z);
		}

		// ---- the chain -------------------------------------------------------------------------

		void gates() {
			if (m.category() == Category.MISC) {
				never(() -> m.shortName() + " is not a natural spawn (misc category)");
			}
			if (!m.canSummon()) {
				never(() -> m.shortName() + " cannot be summoned");
			}
			placement();
			if (!m.allowedInPeaceful() && e.peaceful()) {
				never(() -> "difficulty is peaceful");
			}
			predicate();
			if (!w.spawnBoxFree(m, x, y, z)) {
				never(() -> String.format(Locale.ROOT, "no room for the %s (%.2f wide, %.2f high)", m.shortName(), m.spawnWidth(), m.spawnHeight()));
			}
			walk();
			obstruction();
		}

		void placement() {
			switch (r.placement()) {
				case ON_GROUND -> {
					if (!w.insideBorder(x, y, z)) {
						never(() -> "outside the world border");
					}
					switch (w.floor(x, y - 1, z, m)) {
						case OK -> {
						}
						case NOT_STURDY -> never(() -> name(-1) + ": top face is not solid, not a spawn floor");
						case EMITS_LIGHT -> never(() -> name(-1) + " gives light 14 or more, not a spawn floor");
						case BLOCK_RULE -> never(() -> name(-1) + " is never a spawn floor for " + m.shortName());
					}
					space(0);
					space(1);
				}
				case IN_WATER -> {
					if (!w.insideBorder(x, y, z)) {
						never(() -> "outside the world border");
					}
					if (!w.water(x, y, z)) {
						never(() -> m.shortName() + " spawns only in water");
					}
					if (w.conductor(x, y + 1, z)) {
						never(() -> name(1) + " right above the water");
					}
				}
				case IN_LAVA -> {
					if (!w.insideBorder(x, y, z)) {
						never(() -> "outside the world border");
					}
					if (!w.lava(x, y, z)) {
						never(() -> m.shortName() + " spawns only in lava");
					}
				}
				case NO_RESTRICTIONS -> {
				}
			}
		}

		void space(int dy) {
			String where = dy == 0 ? "" : " (block above)";
			switch (w.space(x, y + dy, z, m)) {
				case OK -> {
				}
				case FULL_BLOCK -> never(() -> dy == 0 ? name(0) + " fills the block" : "only 1 block of space: " + name(1) + " above, mobs need 2");
				case SIGNAL_SOURCE -> never(() -> name(dy) + where + " is a redstone component: no spawning inside");
				case FLUID -> never(() -> name(dy) + where + " holds a fluid: no spawning inside");
				case NO_SPAWN_INSIDE -> never(() -> name(dy) + where + " prevents spawning inside (rails)");
				case DANGEROUS -> never(() -> name(dy) + where + " would hurt the " + m.shortName());
			}
		}

		void floorValid() {
			World.Floor f = w.floor(x, y - 1, z, m);
			if (f != World.Floor.OK) {
				never(() -> name(-1) + " is not a valid spawn floor for " + m.shortName());
			}
		}

		void floorTag(String tag) {
			if (!w.is(x, y - 1, z, tag)) {
				never(() -> m.shortName() + " needs a floor in " + tag + ", not " + name(-1));
			}
		}

		void blockLightLimit() {
			int limit = e.dimension().blockLightLimit();
			if (limit < 15 && blk() > limit) {
				never(() -> "block light " + blk() + " > " + limit + ": monsters need " + limit);
			}
		}

		/** {@code Monster.isDarkEnoughToSpawn} without the block light limit. */
		void darkFactor() {
			Dimension d = e.dimension();
			int sky = sky();
			int blk = blk();
			factor(s -> Light.monsterDark(d, sky, blk, s), s -> {
				int b = Light.monsterTestBrightness(sky, blk, s);
				return "too bright " + s.name() + ": light " + b + " > " + d.lightTestMax();
			});
		}

		void brightAtNoon() {
			int raw0 = Light.raw(sky(), blk(), 0);
			if (raw0 <= 8) {
				never(() -> "too dark: light " + raw0 + " at noon, " + m.shortName() + " needs 9 or more");
			}
		}

		void predicate() {
			switch (r.kind()) {
				case NONE, ALWAYS, NOT_PEACEFUL -> {
				}
				case MOB -> floorValid();
				case MONSTER -> {
					blockLightLimit();
					darkFactor();
					floorValid();
				}
				case ANY_LIGHT_MONSTER -> floorValid();
				case SURFACE_MONSTER -> {
					blockLightLimit();
					darkFactor();
					floorValid();
					if (!w.canSeeSky(x, y, z)) {
						never(() -> m.shortName() + " needs open sky above");
					}
				}
				case STRAY -> {
					blockLightLimit();
					darkFactor();
					floorValid();
					int top = y + 1;
					while (w.is(x, top, z, "minecraft:powder_snow")) {
						top++;
					}
					if (!w.canSeeSky(x, top - 1, z)) {
						never(() -> "stray needs open sky above (powder snow is allowed)");
					}
				}
				case PATROLLING -> {
					if (blk() > 8) {
						never(() -> "block light " + blk() + " > 8: pillagers need 8 or less");
					}
					floorValid();
				}
				case DROWNED -> drowned();
				case GUARDIAN -> {
					if (!w.water(x, y - 1, z)) {
						never(() -> "guardians need water below");
					}
					if (w.canSeeSkyFromBelowWater(x, y, z)) {
						constant(1 / 20.0, "sees the sky through the water: 1 in 20");
					}
				}
				case SLIME -> slime();
				case GHAST -> {
					constant(1 / 20.0, "ghast: 1 in 20");
					floorValid();
				}
				case ZOMBIFIED_PIGLIN, NOT_ON_WART -> {
					if (w.is(x, y - 1, z, "minecraft:nether_wart_block")) {
						never(() -> m.shortName() + " never spawns on nether wart blocks");
					}
				}
				case NO_PLAYER_NEAR -> {
					floorValid();
					note("only when no player is within 5 blocks");
				}
				case STRIDER -> {
					int top = y + 1;
					while (w.lava(x, top, z)) {
						top++;
					}
					if (!w.air(x, top, z)) {
						int above = top;
						never(() -> "striders need air above the lava (" + w.name(x, above, z) + ")");
					}
				}
				case ANIMAL -> {
					floorTag(r.tag());
					brightAtNoon();
				}
				case FLOOR_TAG -> floorTag(r.tag());
				case POLAR_BEAR -> {
					if (w.biomeIs(x, y, z, "#minecraft:polar_bears_spawn_on_alternate_blocks")) {
						brightAtNoon();
						floorTag("#minecraft:polar_bears_spawnable_on_alternate");
					} else {
						floorTag(r.tag());
						brightAtNoon();
					}
				}
				case TURTLE -> {
					if (y >= e.seaLevel() + 4) {
						never(() -> "turtles spawn below sea level + 4 (y < " + (e.seaLevel() + 4) + ")");
					}
					floorTag(SAND);
					brightAtNoon();
				}
				case OCELOT -> constant(2 / 3.0, "ocelot: 2 in 3");
				case BAT -> bat();
				case WATER_BAND -> waterBand(r.bandLow(), r.bandHigh());
				case TROPICAL_FISH -> {
					waterAboveBelow();
					if (!w.biomeIs(x, y, z, "#minecraft:allows_tropical_fish_spawns_at_any_height")) {
						band(-13, 0);
					}
				}
				case GLOW_SQUID -> {
					if (y > e.seaLevel() - 33) {
						never(() -> "glow squid spawn at y ≤ " + (e.seaLevel() - 33));
					}
					if (Light.raw(sky(), blk(), 0) != 0) {
						never(() -> "glow squid need light 0");
					}
					if (!w.waterBlock(x, y, z)) {
						never(() -> "glow squid need a water block");
					}
				}
			}
		}

		void drowned() {
			if (!w.water(x, y - 1, z)) {
				never(() -> "drowned need water below");
			}
			blockLightLimit();
			darkFactor();
			if (w.biomeIs(x, y, z, "#minecraft:more_frequent_drowned_spawns")) {
				constant(1 / 15.0, "river biome: 1 in 15");
			} else {
				if (y >= e.seaLevel() - 5) {
					never(() -> "drowned outside rivers spawn below y " + (e.seaLevel() - 5));
				}
				constant(1 / 40.0, "1 in 40 outside rivers");
			}
		}

		void slime() {
			boolean swampBand = w.biomeIs(x, y, z, "#minecraft:allows_surface_slime_spawns") && y > 50 && y < 70;
			Boolean slimeChunk = w.slimeChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
			boolean chunkBand = y < 40;
			double pc = chunkBand && Boolean.TRUE.equals(slimeChunk) ? 0.1 : 0.0;
			if (chunkBand && slimeChunk == null) {
				unknown = "slime: possible if this is a slime chunk (y < 40); the world seed is unknown (/spawnsight seed <seed>)";
			}
			if (!swampBand && pc == 0) {
				if (unknown == null) {
					never(() -> chunkBand ? "slime: not a slime chunk, and not a swamp at y 51-69" : "slime: needs a slime chunk below y 40 or a swamp at y 51-69");
				}
			}
			int sky = sky();
			int blk = blk();
			factor(s -> {
				double ps = 0;
				if (swampBand) {
					float moon = s == e.now() ? e.surfaceSlimeNow() : e.surfaceSlimeBest();
					ps = moon * Light.atMostRandom(Light.raw(sky, blk, s.darken()), 8);
				}
				return ps + (1 - ps) * pc;
			}, s -> swampBand ? "swamp slime: too bright or wrong moon phase " + s.name() : "slime: not a slime chunk");
			if (swampBand) {
				note("swamp slimes: more at full moon, none at new moon");
			}
			if (pc > 0) {
				note("slime chunk: 1 in 10");
			}
		}

		void bat() {
			if (y >= w.worldSurface(x, z)) {
				never(() -> "bats spawn only below the surface");
			}
			int sky = sky();
			int blk = blk();
			factor(s -> 0.5 * Light.atMostRandom(Light.raw(sky, blk, s.darken()), 4),
					s -> "too bright for bats " + s.name() + ": light " + Light.raw(sky, blk, s.darken()) + " > 3");
			floorTag("#minecraft:bats_spawnable_on");
		}

		void waterAboveBelow() {
			if (!w.water(x, y - 1, z)) {
				never(() -> m.shortName() + " needs water below");
			}
			if (!w.waterBlock(x, y + 1, z)) {
				never(() -> m.shortName() + " needs a water block above");
			}
		}

		void band(int low, int high) {
			int lo = e.seaLevel() + low;
			int hi = e.seaLevel() + high;
			if (y < lo || y > hi) {
				never(() -> m.shortName() + " spawns at y " + lo + " to " + hi);
			}
		}

		void waterBand(int low, int high) {
			band(low, high);
			waterAboveBelow();
		}

		void walk() {
			float amb = e.dimension().ambientLight();
			int sky = sky();
			int blk = blk();
			switch (r.walk()) {
				case NONE -> {
				}
				case HOGLIN -> note("not near warped fungus, nether portals or respawn anchors (not checked)");
				case MONSTER -> monsterWalk(amb, sky, blk);
				case SILVERFISH -> {
					if (!w.is(x, y - 1, z, INFESTABLE)) {
						monsterWalk(amb, sky, blk);
					}
				}
				case GUARDIAN -> {
					if (!w.water(x, y, z)) {
						monsterWalk(amb, sky, blk);
					}
				}
				case ANIMAL -> {
					if (!w.is(x, y - 1, z, GRASS)) {
						brightWalk(amb, sky, blk, "off grass blocks");
					}
				}
				case MOOSHROOM -> {
					if (!w.is(x, y - 1, z, "minecraft:mycelium")) {
						brightWalk(amb, sky, blk, "off mycelium");
					}
				}
				case TURTLE -> {
					if (!w.water(x, y, z) && !w.is(x, y - 1, z, SAND)) {
						brightWalk(amb, sky, blk, "off sand");
					}
				}
			}
		}

		void monsterWalk(float amb, int sky, int blk) {
			factor(s -> Light.monsterWalkOk(Light.raw(sky, blk, s.darken()), amb) ? 1 : 0,
					s -> "too bright " + s.name() + " for a monster to stay (light " + Light.raw(sky, blk, s.darken()) + ")");
		}

		void brightWalk(float amb, int sky, int blk, String where) {
			factor(s -> Light.brightWalkOk(Light.raw(sky, blk, s.darken()), amb) ? 1 : 0,
					s -> m.shortName() + " needs light " + minBright(amb) + "+ " + where + " (" + Light.raw(sky, blk, s.darken()) + " " + s.name() + ")");
		}

		void obstruction() {
			switch (r.obstruction()) {
				case ENTITIES_ONLY -> {
				}
				case NO_LIQUID -> {
					if (w.liquidInBox(m, x, y, z)) {
						never(() -> "water or lava inside the " + m.shortName() + "'s hitbox");
					}
				}
				case OCELOT -> {
					if (w.liquidInBox(m, x, y, z)) {
						never(() -> "water or lava inside the ocelot's hitbox");
					}
					if (y < e.seaLevel()) {
						never(() -> "ocelots spawn at sea level or higher");
					}
					if (!w.is(x, y - 1, z, GRASS) && !w.is(x, y - 1, z, LEAVES)) {
						never(() -> "ocelots need grass blocks or leaves below");
					}
				}
			}
			note("no entity may be in the way (not checked)");
		}
	}

	/** Lowest brightness that passes the off-grass animal walk target in a dimension with this ambient light. */
	static int minBright(float ambient) {
		for (int b = 0; b <= 15; b++) {
			if (Light.brightWalkOk(b, ambient)) {
				return b;
			}
		}
		return 16;
	}

	static String pct(double p) {
		return p >= 0.995 ? "100 %" : p >= 0.01 ? String.format(Locale.ROOT, "%.0f %%", p * 100) : String.format(Locale.ROOT, "%.2f %%", p * 100);
	}
}
