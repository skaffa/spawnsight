package dev.spawnsight.core.rules;

import java.util.List;

/**
 * The world state that is the same for every block: dimension rules, difficulty, sea level, the light
 * situation now and the situations to compare with ("sometimes" = possible in one of {@link #later}).
 *
 * @param now               current sky darkening and thunder
 * @param later             other situations the world goes through (night, thunderstorm...)
 * @param surfaceSlimeNow   {@code gameplay/surface_slime_spawn_chance} now (moon phase)
 * @param surfaceSlimeBest  the highest value it reaches (full moon)
 */
public record Env(Dimension dimension, boolean peaceful, int seaLevel, Scenario now, List<Scenario> later,
		float surfaceSlimeNow, float surfaceSlimeBest) {

	/**
	 * A light situation.
	 *
	 * @param darken {@code Level.getSkyDarken()}: 15 − {@code gameplay/sky_light_level}
	 * @param thunder thundering: monsters then test {@code max(sky − 10, block)} instead of the current darkening
	 */
	public record Scenario(String name, int darken, boolean thunder) {
	}

	/**
	 * Vanilla overworld situations (26.3 {@code timeline/day.json} and {@code WeatherAttributes}): sky light
	 * level 15 by day and 4 by night; full rain blends it 31.25 % towards 4 (11.56: darkening 3), full
	 * thunder 52.7 % (9.2: darkening 5); every night has 11. Measured on the client with the M1 self-test.
	 */
	public static final List<Scenario> OVERWORLD_LATER = List.of(
			new Scenario("by day", 0, false),
			new Scenario("in daytime rain", 3, false),
			new Scenario("at night", 11, false),
			new Scenario("in a daytime thunderstorm", 5, true),
			new Scenario("in a thunderstorm at night", 11, true));

	/** Every situation, now first. */
	public List<Scenario> all() {
		List<Scenario> out = new java.util.ArrayList<>(later.size() + 1);
		out.add(now);
		out.addAll(later);
		return out;
	}
}
