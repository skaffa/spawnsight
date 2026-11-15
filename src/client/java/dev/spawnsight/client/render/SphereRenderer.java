package dev.spawnsight.client.render;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.core.spheres.Spheres;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The spawn and despawn spheres around the player's feet: 24 (no spawning closer),
 * 32 (random despawn beyond), 128 (instant despawn). Drawn with vanilla gizmos, collected every tick.
 */
public final class SphereRenderer {
	public static final int NO_SPAWN = 0xFFFF5040;
	public static final int RANDOM_DESPAWN = 0xFFFFC040;
	public static final int INSTANT_DESPAWN = 0xFF60A0FF;
	private static final List<double[]> S24 = Spheres.wireframe(24, 7, 6, 48);
	private static final List<double[]> S32 = Spheres.wireframe(32, 7, 6, 64);
	private static final List<double[]> S128 = Spheres.wireframe(128, 9, 8, 128);

	private SphereRenderer() {
	}

	public static void tick(Minecraft mc) {
		if (!SpawnsightClient.config.showSpheres || mc.player == null || mc.level == null) {
			return;
		}
		Vec3 c = mc.player.position();
		draw(c, S24, NO_SPAWN, 2.5F);
		draw(c, S32, RANDOM_DESPAWN, 2.0F);
		draw(c, S128, INSTANT_DESPAWN, 1.5F);
	}

	private static void draw(Vec3 c, List<double[]> lines, int argb, float width) {
		for (double[] l : lines) {
			Gizmos.line(new Vec3(c.x + l[0], c.y + l[1], c.z + l[2]), new Vec3(c.x + l[3], c.y + l[4], c.z + l[5]), argb, width);
		}
	}
}
