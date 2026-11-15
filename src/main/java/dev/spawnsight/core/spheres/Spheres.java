package dev.spawnsight.core.spheres;

import dev.spawnsight.core.rules.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-distance rules of 26.3 {@code NaturalSpawner} and {@code Mob.checkDespawn}, and
 * the line segments for drawing the spheres.
 */
public final class Spheres {
	/** No natural spawn within this distance of a player or of the world spawn point. */
	public static final int NO_SPAWN = 24;

	private Spheres() {
	}

	/** Distance zone of a mob from the nearest player. */
	public enum Zone {
		/** Closer than 24: no natural spawning. */
		TOO_CLOSE,
		/** 24 to 32: spawns, never despawns at random. */
		SAFE,
		/** 32 to the instant-despawn distance: random despawn after 30 s idle. */
		RANDOM_DESPAWN,
		/** Beyond 128 (64 for water ambient): despawns at once; monsters cannot spawn there. */
		INSTANT_DESPAWN
	}

	/** Squared distance from the spawn point of block (x, y, z), i.e. (x + 0.5, y, z + 0.5), to the player's feet. */
	public static double distSqr(double px, double py, double pz, int x, int y, int z) {
		double dx = x + 0.5 - px;
		double dy = y - py;
		double dz = z + 0.5 - pz;
		return dx * dx + dy * dy + dz * dz;
	}

	/** {@code isRightDistanceToPlayerAndSpawnPoint}: strictly more than 24 blocks (576 squared). */
	public static boolean farEnough(double distSqr) {
		return distSqr > NO_SPAWN * NO_SPAWN;
	}

	public static Zone zone(double distSqr, Category category) {
		if (!farEnough(distSqr)) {
			return Zone.TOO_CLOSE;
		}
		int keep = Category.NO_DESPAWN_DISTANCE;
		int gone = category.despawnDistance;
		if (distSqr > (double) gone * gone) {
			return Zone.INSTANT_DESPAWN;
		}
		return distSqr > (double) keep * keep ? Zone.RANDOM_DESPAWN : Zone.SAFE;
	}

	/**
	 * Wire-frame sphere of radius {@code r} around the origin: {@code rings} latitude circles plus
	 * {@code meridians} longitude half-circles, each made of {@code segments} pieces. Every element is one
	 * line segment {x1, y1, z1, x2, y2, z2}.
	 */
	public static List<double[]> wireframe(double r, int rings, int meridians, int segments) {
		List<double[]> out = new ArrayList<>();
		for (int i = 1; i <= rings; i++) {
			double lat = Math.PI * i / (rings + 1) - Math.PI / 2;
			double y = r * Math.sin(lat);
			double rr = r * Math.cos(lat);
			for (int s = 0; s < segments; s++) {
				double a0 = 2 * Math.PI * s / segments;
				double a1 = 2 * Math.PI * (s + 1) / segments;
				out.add(new double[] {rr * Math.cos(a0), y, rr * Math.sin(a0), rr * Math.cos(a1), y, rr * Math.sin(a1)});
			}
		}
		for (int m = 0; m < meridians; m++) {
			double lon = Math.PI * m / meridians;
			double cx = Math.cos(lon);
			double cz = Math.sin(lon);
			for (int s = 0; s < segments; s++) {
				double a0 = 2 * Math.PI * s / segments;
				double a1 = 2 * Math.PI * (s + 1) / segments;
				out.add(new double[] {r * Math.cos(a0) * cx, r * Math.sin(a0), r * Math.cos(a0) * cz,
						r * Math.cos(a1) * cx, r * Math.sin(a1), r * Math.cos(a1) * cz});
			}
		}
		return out;
	}
}
