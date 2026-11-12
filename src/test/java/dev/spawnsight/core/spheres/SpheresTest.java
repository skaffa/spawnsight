package dev.spawnsight.core.spheres;

import static org.junit.jupiter.api.Assertions.*;

import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.spheres.Spheres.Zone;

import org.junit.jupiter.api.Test;

class SpheresTest {
	@Test
	void exactlyTwentyFourIsTooClose() {
		assertFalse(Spheres.farEnough(576.0));
		assertTrue(Spheres.farEnough(576.0001));
	}

	@Test
	void distanceUsesBlockBottomCentre() {
		// player standing at (0.5, 64, 0.5): block (24, 64, 0) is 24 away exactly
		assertEquals(576.0, Spheres.distSqr(0.5, 64, 0.5, 24, 64, 0), 1e-9);
	}

	@Test
	void zones() {
		assertEquals(Zone.TOO_CLOSE, Spheres.zone(20 * 20, Category.MONSTER));
		assertEquals(Zone.SAFE, Spheres.zone(30 * 30, Category.MONSTER));
		assertEquals(Zone.RANDOM_DESPAWN, Spheres.zone(100 * 100, Category.MONSTER));
		assertEquals(Zone.INSTANT_DESPAWN, Spheres.zone(129 * 129, Category.MONSTER));
		assertEquals(Zone.INSTANT_DESPAWN, Spheres.zone(65 * 65, Category.WATER_AMBIENT));
	}

	@Test
	void wireframePointsLieOnTheSphere() {
		var lines = Spheres.wireframe(24, 5, 4, 32);
		assertEquals(5 * 32 + 4 * 32, lines.size());
		for (double[] l : lines) {
			assertEquals(24, Math.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]), 1e-9);
			assertEquals(24, Math.sqrt(l[3] * l[3] + l[4] * l[4] + l[5] * l[5]), 1e-9);
		}
	}
}
