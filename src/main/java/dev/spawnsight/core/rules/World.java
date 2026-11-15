package dev.spawnsight.core.rules;

/**
 * Minecraft-free view of the world around one candidate spawn block. Coordinates are block
 * coordinates; "pos" in the comments is the block the mob's feet would be in. On the client every
 * method calls the matching vanilla method; tests use a fake grid.
 */
public interface World {
	/** Result of {@code BlockState.isValidSpawn} on a floor block, with the reason when false. */
	enum Floor {
		OK,
		/** Top face is not sturdy (bottom slab, carpet, glass pane, air...). */
		NOT_STURDY,
		/** Light emission 14 or more (glowstone, sea lantern...). */
		EMITS_LIGHT,
		/** The block's own rule says no for this mob (glass, leaves, trapdoors, ice, magma...). */
		BLOCK_RULE
	}

	/** Result of {@code NaturalSpawner.isValidEmptySpawnBlock} for a block the mob would stand in. */
	enum Space {
		OK,
		FULL_BLOCK,
		SIGNAL_SOURCE,
		FLUID,
		/** In tag {@code #minecraft:prevent_mob_spawning_inside} (rails). */
		NO_SPAWN_INSIDE,
		/** {@code EntityType.isBlockDangerous}: fire, magma, campfire, berry bush, cactus, wither rose, powder snow. */
		DANGEROUS
	}

	/** Display name of the block, for messages ("Oak Slab"). */
	String name(int x, int y, int z);

	Floor floor(int x, int y, int z, MobSpec mob);

	Space space(int x, int y, int z, MobSpec mob);

	/** Fluid state has tag {@code #minecraft:water} (includes waterlogged blocks). */
	boolean water(int x, int y, int z);

	/** Fluid state has tag {@code #minecraft:lava}. */
	boolean lava(int x, int y, int z);

	/** Block is {@code minecraft:water} (a water block, not a waterlogged one). */
	boolean waterBlock(int x, int y, int z);

	boolean air(int x, int y, int z);

	/** {@code BlockState.isRedstoneConductor}. */
	boolean conductor(int x, int y, int z);

	/** Block id ({@code "minecraft:grass_block"}) or block tag ({@code "#minecraft:animals_spawnable_on"}). */
	boolean is(int x, int y, int z, String blockOrTag);

	/** {@code level.noCollision(type.getSpawnAABB(x + 0.5, y, z + 0.5))}. */
	boolean spawnBoxFree(MobSpec mob, int x, int y, int z);

	/** {@code level.containsAnyLiquid(mob bounding box)} for a mob placed at (x + 0.5, y, z + 0.5). */
	boolean liquidInBox(MobSpec mob, int x, int y, int z);

	int skyLight(int x, int y, int z);

	int blockLight(int x, int y, int z);

	boolean canSeeSky(int x, int y, int z);

	/** {@code LevelReader.canSeeSkyFromBelowWater}. */
	boolean canSeeSkyFromBelowWater(int x, int y, int z);

	/** Y of the {@code WORLD_SURFACE} heightmap position (first free block above the top block). */
	int worldSurface(int x, int z);

	boolean insideBorder(int x, int y, int z);

	/** Biome at the block has this tag ({@code "#minecraft:allows_surface_slime_spawns"}). */
	boolean biomeIs(int x, int y, int z, String tag);

	/** Whether chunk (cx, cz) is a slime chunk, or {@code null} when the seed is unknown. */
	Boolean slimeChunk(int chunkX, int chunkZ);
}
