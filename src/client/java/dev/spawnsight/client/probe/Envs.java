package dev.spawnsight.client.probe;

import dev.spawnsight.core.rules.Dimension;
import dev.spawnsight.core.rules.Env;
import dev.spawnsight.core.table.SpawnTable;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.Difficulty;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.List;

/** Builds the {@link Env} for a level from what that level knows (PLAN.md §7). */
public final class Envs {
	private Envs() {
	}

	/**
	 * @param surfaceSlimeNow the current {@code gameplay/surface_slime_spawn_chance}, or NaN to derive it from
	 *                        the synced moon phase and the vanilla moon timeline in the table
	 */
	public static Env of(Level level, SpawnTable table, float surfaceSlimeNow) {
		DimensionType dt = level.dimensionType();
		IntProvider test = dt.monsterSpawnLightTest();
		Dimension d = new Dimension(level.dimension().identifier().toString(), dt.monsterSpawnBlockLightLimit(), test.minInclusive(),
				test.maxInclusive(), dt.ambientLight());
		boolean dayCycle = level.dimension() == Level.OVERWORLD;
		Env.Scenario now = new Env.Scenario("now", level.getSkyDarken(), level.isThundering());
		float best = table.surfaceSlimeBest();
		float slimeNow = surfaceSlimeNow;
		if (Float.isNaN(slimeNow)) {
			int phase = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.MOON_PHASE).index();
			float[] byPhase = table.surfaceSlimeByMoonPhase;
			slimeNow = phase >= 0 && phase < byPhase.length ? byPhase[phase] : best;
		}
		return new Env(d, level.getDifficulty() == Difficulty.PEACEFUL, level.getSeaLevel(), now, dayCycle ? Env.OVERWORLD_LATER : List.of(),
				slimeNow, Math.max(best, slimeNow));
	}
}
