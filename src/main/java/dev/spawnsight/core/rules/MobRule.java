package dev.spawnsight.core.rules;

/**
 * How one entity type is checked: its {@code SpawnPlacements} registration (placement type and spawn
 * predicate) plus the instance checks after creation (walk target, spawn obstruction).
 *
 * @param tag block tag for {@link Kind#ANIMAL} / {@link Kind#FLOOR_TAG} / {@link Kind#POLAR_BEAR}; band limits for
 *            {@link Kind#WATER_BAND} are in {@code bandLow}/{@code bandHigh} (relative to sea level)
 */
public record MobRule(Placement placement, Kind kind, String tag, int bandLow, int bandHigh, Walk walk, Obstruction obstruction) {

	/** {@code SpawnPlacementTypes}. */
	public enum Placement {
		ON_GROUND,
		IN_WATER,
		IN_LAVA,
		NO_RESTRICTIONS
	}

	/** The registered spawn predicate. */
	public enum Kind {
		/** No registration: always true. */
		NONE,
		/** {@code Mob.checkMobSpawnRules}: floor {@code isValidSpawn}. */
		MOB,
		/** {@code Monster.checkMonsterSpawnRules}: dark enough + floor. */
		MONSTER,
		/** {@code checkAnyLightMonsterSpawnRules}: floor only. */
		ANY_LIGHT_MONSTER,
		/** {@code checkSurfaceMonstersSpawnRules}: monster + sees the sky. */
		SURFACE_MONSTER,
		/** {@code Stray}: monster + sees the sky above powder snow. */
		STRAY,
		/** {@code checkPatrollingMonsterSpawnRules}: block light ≤ 8 + floor. */
		PATROLLING,
		DROWNED,
		GUARDIAN,
		SLIME,
		/** Magma cube: not peaceful. */
		NOT_PEACEFUL,
		/** Sulfur cube: always true. */
		ALWAYS,
		/** Ghast: not peaceful, 1/20, floor. */
		GHAST,
		ZOMBIFIED_PIGLIN,
		/** Piglin, hoglin: floor is not a nether wart block. */
		NOT_ON_WART,
		/** Silverfish, endermite: floor + no player within 5 blocks. */
		NO_PLAYER_NEAR,
		STRIDER,
		/** {@code Animal.checkAnimalSpawnRules} and look-alikes: floor in {@code tag} + raw light at full day > 8. */
		ANIMAL,
		/** Axolotl: floor in {@code tag}, no light rule. */
		FLOOR_TAG,
		POLAR_BEAR,
		TURTLE,
		/** Ocelot: 2 in 3. */
		OCELOT,
		BAT,
		/** Fish, squid, dolphin, nautilus: water below, water block above, Y within sea level + [bandLow, bandHigh]. */
		WATER_BAND,
		TROPICAL_FISH,
		GLOW_SQUID
	}

	/** {@code PathfinderMob.getWalkTargetValue(pos) >= 0} after creation. */
	public enum Walk {
		/** Not a pathfinder mob, or the value is always ≥ 0. */
		NONE,
		/** {@code Monster}: −(light magic − 0.5). */
		MONSTER,
		/** {@code Animal}: 10 on a grass block, else light magic − 0.5. */
		ANIMAL,
		/** Mooshroom: 10 on mycelium, else light. */
		MOOSHROOM,
		/** Turtle: 10 in water or on sand, else light. */
		TURTLE,
		/** Silverfish: 10 on an infestable block, else monster light. */
		SILVERFISH,
		/** Guardian: 10 + light cost in water (always ≥ 0), else monster light. */
		GUARDIAN,
		/** Hoglin: −1 near a repellent (warped fungus...), else ≥ 0. Repellents are not checked. */
		HOGLIN
	}

	/** {@code Mob.checkSpawnObstruction}. Entity collisions are never checked (they change all the time). */
	public enum Obstruction {
		/** No liquid in the bounding box (default). */
		NO_LIQUID,
		/** Only entities (water mobs, axolotl, guardian, strider, drowned). */
		ENTITIES_ONLY,
		/** Ocelot: no liquid, Y ≥ sea level, grass block or leaves below. */
		OCELOT
	}

	public static MobRule of(Placement p, Kind k, Walk w, Obstruction o) {
		return new MobRule(p, k, null, 0, 0, w, o);
	}

	public static MobRule tagged(Placement p, Kind k, String tag, Walk w, Obstruction o) {
		return new MobRule(p, k, tag, 0, 0, w, o);
	}

	public static MobRule band(int low, int high, Walk w) {
		return new MobRule(Placement.IN_WATER, Kind.WATER_BAND, null, low, high, w, Obstruction.ENTITIES_ONLY);
	}

	/** An entity type without a {@code SpawnPlacements} registration: no restrictions, predicate true. */
	public static final MobRule UNREGISTERED = of(Placement.NO_RESTRICTIONS, Kind.NONE, Walk.NONE, Obstruction.NO_LIQUID);
}
