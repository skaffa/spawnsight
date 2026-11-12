package dev.spawnsight.core.rules;

import java.util.Random;

/**
 * 26.3 slime chunk test: {@code WorldgenRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) == 0}.
 * The vanilla random source is the same 48-bit LCG as {@link java.util.Random}. The int overflow of the
 * seed formula is kept on purpose: {@code x*x*4987142}, {@code x*5947611} and {@code z*389711} are int
 * products, only {@code z*z*4392871L} is long.
 */
public final class SlimeChunks {
	public static final long SALT = 987234911L;

	private SlimeChunks() {
	}

	public static boolean isSlimeChunk(long worldSeed, int cx, int cz) {
		long s = worldSeed + cx * cx * 4987142 + cx * 5947611 + cz * cz * 4392871L + cz * 389711 ^ SALT;
		return new Random(s).nextInt(10) == 0;
	}
}
