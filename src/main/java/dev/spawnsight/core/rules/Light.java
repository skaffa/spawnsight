package dev.spawnsight.core.rules;

/** Light formulas from 26.3 {@code LevelReader}, {@code Monster} and {@code PathfinderMob}, as chances. */
public final class Light {
	private Light() {
	}

	/** {@code getRawBrightness(pos, darken)} = max(sky − darken, block). */
	public static int raw(int sky, int block, int darken) {
		return Math.max(block, sky - darken);
	}

	/** {@code getLightLevelDependentMagicValue}: lerp(ambient, v / (4 − 3v), 1) with v = brightness / 15, in float like vanilla. */
	public static float magic(int brightness, float ambient) {
		float v = brightness / 15.0F;
		float curved = v / (4.0F - 3.0F * v);
		return curved + ambient * (1.0F - curved);
	}

	/** Monster walk target {@code -(magic - 0.5) >= 0}: true when dark enough to count as a walk target. */
	public static boolean monsterWalkOk(int brightness, float ambient) {
		return -(magic(brightness, ambient) - 0.5F) >= 0.0F;
	}

	/** Animal walk target off grass: {@code magic - 0.5 >= 0}. */
	public static boolean brightWalkOk(int brightness, float ambient) {
		return magic(brightness, ambient) - 0.5F >= 0.0F;
	}

	/** Chance that {@code value > random.nextInt(bound)} fails, i.e. {@code value <= nextInt(bound)}: (bound − value) / bound. */
	public static double atMostRandom(int value, int bound) {
		if (value < 0) {
			return 1.0;
		}
		return Math.max(0, bound - value) / (double) bound;
	}

	/** Chance that a uniform integer in [min, max] is at least {@code b}. */
	public static double uniformAtLeast(int min, int max, int b) {
		if (b <= min) {
			return 1.0;
		}
		if (b > max) {
			return 0.0;
		}
		return (max - b + 1) / (double) (max - min + 1);
	}

	/** The brightness {@code Monster.isDarkEnoughToSpawn} tests in this situation. */
	public static int monsterTestBrightness(int sky, int block, Env.Scenario s) {
		return raw(sky, block, s.thunder() ? 10 : s.darken());
	}

	/**
	 * Chance that {@code Monster.isDarkEnoughToSpawn} passes, block light limit already checked:
	 * sky light ≤ nextInt(32), then the dimension's light test.
	 */
	public static double monsterDark(Dimension d, int sky, int block, Env.Scenario s) {
		double pSky = atMostRandom(sky, 32);
		return pSky * uniformAtLeast(d.lightTestMin(), d.lightTestMax(), monsterTestBrightness(sky, block, s));
	}
}
