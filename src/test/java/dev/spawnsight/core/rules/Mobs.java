package dev.spawnsight.core.rules;

/** Vanilla 26.3 entity dimensions ({@code EntityTypes}) for tests. */
final class Mobs {
	private Mobs() {
	}

	static MobSpec monster(String name, double w, double h) {
		return new MobSpec("minecraft:" + name, Category.MONSTER, w, h, w, h, false, false, false, true);
	}

	static MobSpec creature(String name, Category c, double w, double h) {
		return new MobSpec("minecraft:" + name, c, w, h, w, h, false, true, false, true);
	}

	static final MobSpec ZOMBIE = monster("zombie", 0.6, 1.95);
	static final MobSpec SKELETON = monster("skeleton", 0.6, 1.99);
	static final MobSpec SPIDER = monster("spider", 1.4, 0.9);
	static final MobSpec ENDERMAN = monster("enderman", 0.6, 2.9);
	static final MobSpec HUSK = monster("husk", 0.6, 1.95);
	static final MobSpec DROWNED = monster("drowned", 0.6, 1.95);
	static final MobSpec ZOMBIE_HORSE = monster("zombie_horse", 1.3964844, 1.6);
	static final MobSpec CAMEL_HUSK = monster("camel_husk", 1.7, 2.375);
	static final MobSpec PIGLIN = monster("piglin", 0.6, 1.95);
	static final MobSpec ZOMBIFIED_PIGLIN = monster("zombified_piglin", 0.6, 1.95);
	/** Spawn box scaled 4×, created mob at size 1. */
	static final MobSpec SLIME = new MobSpec("minecraft:slime", Category.MONSTER, 2.08, 2.08, 0.52, 0.52, false, false, false, true);
	static final MobSpec PILLAGER = monster("pillager", 0.6, 1.95);
	static final MobSpec COW = creature("cow", Category.CREATURE, 0.9, 1.4);
	static final MobSpec GOAT = creature("goat", Category.CREATURE, 0.9, 1.3);
	static final MobSpec BAT = creature("bat", Category.AMBIENT, 0.5, 0.9);
	static final MobSpec SQUID = creature("squid", Category.WATER_CREATURE, 0.8, 0.8);
}
