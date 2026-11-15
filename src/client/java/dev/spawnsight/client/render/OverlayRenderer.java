package dev.spawnsight.client.render;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.client.scan.Scanner;
import dev.spawnsight.core.config.SpawnsightConfig;
import dev.spawnsight.core.rules.SpotEvaluator.Shade;
import dev.spawnsight.core.spheres.Spheres;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the overlay: one flat square per block on the floor of the spawn position, coloured by shade.
 * Blocks within 24 blocks of the player, where nothing spawns while you stand there, are
 * drawn smaller and fainter so they read as "would spawn if you were away". Each section keeps
 * a compact list of its coloured blocks, rebuilt when the scanner recomputes it.
 */
public final class OverlayRenderer {
	/** Colours without alpha: red, yellow, purple (unknown), green. */
	public static final int RED = 0xE8382E;
	public static final int YELLOW = 0xF2C230;
	public static final int PURPLE = 0xB46BE6;
	public static final int GREEN = 0x4FCB5C;
	private static final float LIFT = 0.02F;
	private static final float INSET = 0.06F;
	private static final float NEAR_INSET = 0.2F;

	/** Coloured blocks of a section: local index in the low 12 bits, shade above. */
	private record Mesh(int version, int[] entries) {
	}

	private static final Long2ObjectOpenHashMap<Mesh> MESHES = new Long2ObjectOpenHashMap<>();

	/** Quads drawn last frame, for stats. */
	public static int lastQuads;
	/** Total nanos spent writing overlay vertices, and frames counted (for the self-test). */
	public static long drawNanos;
	public static long drawFrames;

	private OverlayRenderer() {
	}

	public static int color(int shade) {
		return switch (Shade.values()[shade]) {
			case HOSTILE_NOW -> RED;
			case HOSTILE_LATER -> YELLOW;
			case UNKNOWN -> PURPLE;
			case PASSIVE -> GREEN;
			case NONE -> 0;
		};
	}

	public static void render(LevelRenderContext context) {
		SpawnsightConfig c = SpawnsightClient.config;
		Minecraft mc = Minecraft.getInstance();
		if (!c.overlay || mc.player == null || Scanner.cachedSections() == 0) {
			lastQuads = 0;
			MESHES.clear();
			return;
		}
		Vec3 cam = mc.gameRenderer.mainCamera().position();
		Vec3 feet = mc.player.position();
		int alpha = Math.round(c.opacity * 2.55F);
		double r2 = (double) c.radius * c.radius;
		PoseStack pose = context.poseStack();
		pose.pushPose();
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buf) -> {
			long t0 = System.nanoTime();
			int quads = 0;
			for (var e : Scanner.sections().long2ObjectEntrySet()) {
				Scanner.Section s = e.getValue();
				long key = e.getLongKey();
				if (s.empty()) {
					continue;
				}
				Mesh m = MESHES.get(key);
				if (m == null || m.version != s.version) {
					m = build(s);
					MESHES.put(key, m);
				}
				int bx = SectionPos.x(key) << 4;
				int by = SectionPos.y(key) << 4;
				int bz = SectionPos.z(key) << 4;
				for (int entry : m.entries) {
					int i = entry & 0xFFF;
					int shade = entry >>> 12;
					if (shade == Shade.HOSTILE_LATER.ordinal() && !c.showSometimes) {
						continue;
					}
					int x = bx + (i & 15);
					int y = by + (i >> 8);
					int z = bz + ((i >> 4) & 15);
					double d2 = Spheres.distSqr(feet.x, feet.y, feet.z, x, y, z);
					if (d2 > r2) {
						continue;
					}
					boolean near = !Spheres.farEnough(d2);
					if (near && c.strictDistance && shade != Shade.PASSIVE.ordinal()) {
						continue;
					}
					int a = near ? alpha * 11 / 20 : alpha;
					quad(p, buf, (float) (x - cam.x), (float) (y + LIFT - cam.y), (float) (z - cam.z), (a << 24) | color(shade), near ? NEAR_INSET : INSET);
					quads++;
				}
			}
			lastQuads = quads;
			drawNanos += System.nanoTime() - t0;
			drawFrames++;
		});
		pose.popPose();
		MESHES.keySet().removeIf(k -> Scanner.section(k) == null);
	}

	private static Mesh build(Scanner.Section s) {
		int n = 0;
		for (int i = 0; i < 4096; i++) {
			if (s.shadeAt(i) != 0) {
				n++;
			}
		}
		int[] out = new int[n];
		int k = 0;
		for (int i = 0; i < 4096; i++) {
			int sh = s.shadeAt(i);
			if (sh != 0) {
				out[k++] = (sh << 12) | i;
			}
		}
		return new Mesh(s.version, out);
	}

	/** A square on the block floor, {@code inset} smaller on each side. */
	private static void quad(PoseStack.Pose p, VertexConsumer buf, float x, float y, float z, int argb, float inset) {
		float x0 = x + inset;
		float x1 = x + 1 - inset;
		float z0 = z + inset;
		float z1 = z + 1 - inset;
		buf.addVertex(p, x0, y, z0).setColor(argb);
		buf.addVertex(p, x0, y, z1).setColor(argb);
		buf.addVertex(p, x1, y, z1).setColor(argb);
		buf.addVertex(p, x1, y, z0).setColor(argb);
	}
}
