package dev.spawnsight.core.rules;

/**
 * Light rules of a dimension type ({@code data/minecraft/dimension_type/*.json} in 26.3).
 *
 * @param blockLightLimit {@code monster_spawn_block_light_limit}: monsters need block light ≤ this (15 = no limit)
 * @param lightTestMin    {@code monster_spawn_light_level} lower bound (a constant has min = max)
 * @param lightTestMax    upper bound, inclusive
 * @param ambientLight    {@code ambient_light}, used by the walk-target light formula
 */
public record Dimension(String id, int blockLightLimit, int lightTestMin, int lightTestMax, float ambientLight) {
	public static final Dimension OVERWORLD = new Dimension("minecraft:overworld", 0, 0, 7, 0.0F);
	public static final Dimension NETHER = new Dimension("minecraft:the_nether", 15, 7, 7, 0.1F);
	public static final Dimension END = new Dimension("minecraft:the_end", 0, 15, 15, 0.25F);
}
