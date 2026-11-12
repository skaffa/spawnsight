package dev.spawnsight.core.rules;

import static dev.spawnsight.core.rules.MobRule.Kind.*;
import static dev.spawnsight.core.rules.MobRule.Obstruction.*;
import static dev.spawnsight.core.rules.MobRule.Placement.*;

import dev.spawnsight.core.rules.MobRule.Kind;
import dev.spawnsight.core.rules.MobRule.Walk;

import java.util.HashMap;
import java.util.Map;

/**
 * The vanilla 26.3 {@code SpawnPlacements} table plus each class's walk-target and obstruction override,
 * read from the decompiled code (PLAN.md §3). Keyed by entity id.
 */
public final class MobRules {
	private static final Map<String, MobRule> RULES = new HashMap<>();

	private MobRules() {
	}

	/** The rule for an entity id; {@link MobRule#UNREGISTERED} for types vanilla does not register. */
	public static MobRule get(String id) {
		return RULES.getOrDefault(id, MobRule.UNREGISTERED);
	}

	public static boolean isKnown(String id) {
		return RULES.containsKey(id);
	}

	private static void put(String name, MobRule r) {
		RULES.put("minecraft:" + name, r);
	}

	private static void ground(String name, Kind k, Walk w) {
		put(name, MobRule.of(ON_GROUND, k, w, NO_LIQUID));
	}

	private static void animal(String name, String tag) {
		put(name, MobRule.tagged(ON_GROUND, ANIMAL, "#minecraft:" + tag, Walk.ANIMAL, NO_LIQUID));
	}

	static {
		// water
		put("axolotl", MobRule.tagged(IN_WATER, FLOOR_TAG, "#minecraft:axolotls_spawnable_on", Walk.NONE, ENTITIES_ONLY));
		for (String fish : new String[] {"cod", "pufferfish", "salmon", "squid", "dolphin"}) {
			put(fish, MobRule.band(-13, 0, Walk.NONE));
		}
		put("nautilus", MobRule.band(-25, -5, Walk.NONE));
		put("tropical_fish", MobRule.of(IN_WATER, TROPICAL_FISH, Walk.NONE, ENTITIES_ONLY));
		put("glow_squid", MobRule.of(IN_WATER, GLOW_SQUID, Walk.NONE, ENTITIES_ONLY));
		put("drowned", MobRule.of(IN_WATER, DROWNED, Walk.MONSTER, ENTITIES_ONLY));
		put("guardian", MobRule.of(IN_WATER, GUARDIAN, Walk.GUARDIAN, ENTITIES_ONLY));
		put("elder_guardian", MobRule.of(IN_WATER, GUARDIAN, Walk.GUARDIAN, ENTITIES_ONLY));
		put("strider", MobRule.of(IN_LAVA, STRIDER, Walk.NONE, ENTITIES_ONLY));

		// animals (Animal subclasses use the Animal walk target unless noted)
		for (String a : new String[] {"chicken", "cow", "donkey", "horse", "llama", "mule", "pig", "sheep", "cat",
				"skeleton_horse"}) {
			animal(a, "animals_spawnable_on");
		}
		animal("armadillo", "armadillo_spawnable_on");
		animal("camel", "camels_spawnable_on");
		animal("frog", "frogs_spawnable_on");
		animal("goat", "goats_spawnable_on");
		animal("parrot", "parrots_spawnable_on");
		animal("rabbit", "rabbits_spawnable_on");
		animal("wolf", "wolves_spawnable_on");
		put("mooshroom", MobRule.tagged(ON_GROUND, ANIMAL, "#minecraft:mooshrooms_spawnable_on", Walk.MOOSHROOM, NO_LIQUID));
		put("polar_bear", MobRule.tagged(ON_GROUND, POLAR_BEAR, "#minecraft:animals_spawnable_on", Walk.ANIMAL, NO_LIQUID));
		put("turtle", MobRule.of(ON_GROUND, TURTLE, Walk.TURTLE, NO_LIQUID));
		put("ocelot", MobRule.of(ON_GROUND, Kind.OCELOT, Walk.ANIMAL, MobRule.Obstruction.OCELOT));
		put("hoglin", MobRule.of(ON_GROUND, NOT_ON_WART, Walk.HOGLIN, NO_LIQUID));
		// happy ghast, axolotl, nautilus return walk value 0 or more: overrides above
		put("happy_ghast", MobRule.tagged(ON_GROUND, ANIMAL, "#minecraft:animals_spawnable_on", Walk.NONE, NO_LIQUID));
		ground("bat", BAT, Walk.NONE);

		// monsters
		for (String m : new String[] {"bogged", "cave_spider", "creeper", "giant", "skeleton", "spider", "witch", "wither", "wither_skeleton",
				"zombie", "zombie_villager", "ravager"}) {
			ground(m, MONSTER, Walk.MONSTER); // ravager's obstruction override checks only liquid: same here
		}
		ground("enderman", MONSTER, Walk.NONE);
		ground("creaking", MONSTER, Walk.NONE);
		// zombie horse and camel husk are AbstractHorse/Camel: Animal walk target with monster spawn rules
		ground("zombie_horse", MONSTER, Walk.ANIMAL);
		ground("camel_husk", SURFACE_MONSTER, Walk.ANIMAL);
		ground("husk", SURFACE_MONSTER, Walk.MONSTER);
		ground("parched", SURFACE_MONSTER, Walk.MONSTER);
		ground("stray", STRAY, Walk.MONSTER);
		for (String m : new String[] {"blaze", "breeze", "zoglin"}) {
			ground(m, ANY_LIGHT_MONSTER, Walk.MONSTER);
		}
		ground("endermite", NO_PLAYER_NEAR, Walk.MONSTER);
		ground("silverfish", NO_PLAYER_NEAR, Walk.SILVERFISH);
		ground("pillager", PATROLLING, Walk.NONE);
		ground("piglin", NOT_ON_WART, Walk.MONSTER);
		ground("zombified_piglin", ZOMBIFIED_PIGLIN, Walk.MONSTER);
		ground("slime", SLIME, Walk.NONE);
		ground("magma_cube", NOT_PEACEFUL, Walk.NONE);
		ground("sulfur_cube", ALWAYS, Walk.NONE);
		ground("ghast", GHAST, Walk.NONE);
		for (String m : new String[] {"ender_dragon", "iron_golem", "snow_golem", "villager", "wandering_trader"}) {
			ground(m, MOB, Walk.NONE);
		}

		// no placement restrictions
		for (String m : new String[] {"evoker", "illusioner", "vex", "vindicator"}) {
			put(m, MobRule.of(NO_RESTRICTIONS, MONSTER, Walk.MONSTER, NO_LIQUID));
		}
		put("warden", MobRule.of(NO_RESTRICTIONS, MONSTER, Walk.NONE, NO_LIQUID));
		put("fox", MobRule.tagged(NO_RESTRICTIONS, ANIMAL, "#minecraft:foxes_spawnable_on", Walk.ANIMAL, NO_LIQUID));
		put("panda", MobRule.tagged(NO_RESTRICTIONS, ANIMAL, "#minecraft:animals_spawnable_on", Walk.ANIMAL, NO_LIQUID));
		put("trader_llama", MobRule.tagged(NO_RESTRICTIONS, ANIMAL, "#minecraft:animals_spawnable_on", Walk.ANIMAL, NO_LIQUID));
		put("phantom", MobRule.of(NO_RESTRICTIONS, MOB, Walk.NONE, NO_LIQUID));
		put("shulker", MobRule.of(NO_RESTRICTIONS, MOB, Walk.NONE, NO_LIQUID));
	}
}
