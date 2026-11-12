package dev.spawnsight.core.rules;

/**
 * The facts about one entity type that the rules need. On the client they come from the real
 * {@code EntityType}; tests build them by hand.
 *
 * @param id                    entity id, such as {@code "minecraft:zombie"}
 * @param spawnWidth            width of {@code EntityType.getSpawnAABB} (already scaled by spawnDimensionsScale)
 * @param spawnHeight           height of the spawn box
 * @param boxWidth              width of the freshly created mob's bounding box (liquid check)
 * @param boxHeight             height of that box
 * @param fireImmune            {@code EntityType.fireImmune()}
 * @param allowedInPeaceful     {@code EntityType.isAllowedInPeaceful()}
 * @param canSpawnFarFromPlayer {@code EntityType.canSpawnFarFromPlayer()}
 * @param canSummon             {@code EntityType.canSummon()}
 */
public record MobSpec(String id, Category category, double spawnWidth, double spawnHeight, double boxWidth, double boxHeight,
		boolean fireImmune, boolean allowedInPeaceful, boolean canSpawnFarFromPlayer, boolean canSummon) {

	/** Short name for messages: {@code "zombie"} for {@code "minecraft:zombie"}. */
	public String shortName() {
		int i = id.indexOf(':');
		return (i >= 0 && id.startsWith("minecraft:") ? id.substring(i + 1) : id).replace('_', ' ');
	}
}
