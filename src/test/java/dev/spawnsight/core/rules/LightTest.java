package dev.spawnsight.core.rules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LightTest {
	@Test
	void monsterWalkThresholdsPerDimension() {
		// float maths like vanilla: overworld 12 gives magic 0.50000006, so ≤ 11; nether ≤ 11; end ≤ 10
		assertTrue(Light.monsterWalkOk(11, 0.0F));
		assertFalse(Light.monsterWalkOk(12, 0.0F));
		assertTrue(Light.monsterWalkOk(11, 0.1F));
		assertFalse(Light.monsterWalkOk(12, 0.1F));
		assertTrue(Light.monsterWalkOk(10, 0.25F));
		assertFalse(Light.monsterWalkOk(11, 0.25F));
	}

	@Test
	void animalOffGrassNeedsTwelveInTheOverworld() {
		assertEquals(12, RuleEngine.minBright(0.0F));
		assertFalse(Light.brightWalkOk(11, 0.0F));
	}

	@Test
	void uniformTest() {
		assertEquals(1.0, Light.uniformAtLeast(0, 7, 0));
		assertEquals(1.0 / 8, Light.uniformAtLeast(0, 7, 7));
		assertEquals(0.0, Light.uniformAtLeast(0, 7, 8));
		assertEquals(1.0, Light.uniformAtLeast(7, 7, 7));
		assertEquals(0.0, Light.uniformAtLeast(7, 7, 8));
	}

	@Test
	void skyRoll() {
		assertEquals(1.0, Light.atMostRandom(0, 32));
		assertEquals(17 / 32.0, Light.atMostRandom(15, 32));
		assertEquals(0.0, Light.atMostRandom(4, 4));
	}

	@Test
	void thunderUsesDarkeningTen() {
		Env.Scenario storm = new Env.Scenario("storm", 7, true);
		assertEquals(5, Light.monsterTestBrightness(15, 0, storm));
		assertEquals(8, Light.monsterTestBrightness(15, 0, new Env.Scenario("day", 7, false)));
	}

	@Test
	void slimeChunksMatchVanillaReference() {
		// from WorldgenRandom.seedSlimeChunk in 26.3 (M1 self-test log): seed 12345, z = 0, x 0..15
		StringBuilder found = new StringBuilder();
		for (int x = 0; x < 16; x++) {
			if (SlimeChunks.isSlimeChunk(12345L, x, 0)) {
				found.append(x).append(' ');
			}
		}
		assertEquals("3 8 9 ", found.toString());
	}

	@Test
	void slimeChunkFormulaKeepsIntOverflow() {
		// with int overflow the x term for x = 50000 differs from the long version
		long seed = 12345L;
		int cx = 50000;
		long intVersion = seed + cx * cx * 4987142 + cx * 5947611 ^ SlimeChunks.SALT;
		long longVersion = seed + (long) cx * cx * 4987142L + (long) cx * 5947611L ^ SlimeChunks.SALT;
		assertNotEquals(intVersion, longVersion);
		// about 1 in 10 chunks
		int n = 0;
		for (int x = -50; x < 50; x++) {
			for (int z = -50; z < 50; z++) {
				if (SlimeChunks.isSlimeChunk(seed, x, z)) {
					n++;
				}
			}
		}
		assertTrue(n > 900 && n < 1100, "slime chunks: " + n);
	}
}
