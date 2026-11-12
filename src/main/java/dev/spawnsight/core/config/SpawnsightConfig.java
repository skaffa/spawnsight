package dev.spawnsight.core.config;

/**
 * User settings, stored as {@code config/spawnsight/config.json}. See PLAN.md §6 and §8.
 * Plain fields so Gson can (de)serialize them; call {@link #clamp()} after loading.
 */
public final class SpawnsightConfig {
	/** Overlay on/off (key U). */
	public boolean overlay = false;
	/** Blocks around the player that are scanned and drawn. */
	public int radius = 24;
	/** Green layer: blocks where a passive, ambient or water mob can spawn. */
	public boolean showPassive = false;
	/** Yellow layer: blocks where a hostile mob can spawn only at night, in a thunderstorm or another moon phase. */
	public boolean showSometimes = true;
	/**
	 * Apply the 24-block "no spawn near a player" rule to the colours. Off (default): blocks near you show
	 * what spawns there when you stand elsewhere, hatched.
	 */
	public boolean strictDistance = false;
	/** Spheres around the player: 24 (no spawning), 32 (random despawn), 128 (instant despawn). Key Y. */
	public boolean showSpheres = false;
	/** One-line HUD hint for the aimed block while the overlay is on. */
	public boolean aimHint = true;
	/** Farm filter: only show where this mob can spawn (entity id such as "minecraft:zombie"), or null for all. */
	public String mobFilter = null;
	/** Overlay opacity in percent. */
	public int opacity = 40;
	/** Milliseconds per client tick the scanner may use. */
	public int scanBudgetMs = 2;

	/** Pulls every value into its allowed range and returns {@code this}. */
	public SpawnsightConfig clamp() {
		radius = clamp(radius, 4, 64);
		opacity = clamp(opacity, 10, 100);
		scanBudgetMs = clamp(scanBudgetMs, 1, 10);
		if (mobFilter != null && mobFilter.isBlank()) {
			mobFilter = null;
		}
		return this;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
