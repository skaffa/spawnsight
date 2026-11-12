package dev.spawnsight.client.dev;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.client.Why;
import dev.spawnsight.client.source.SectionLists;
import dev.spawnsight.core.rules.SlimeChunks;
import dev.spawnsight.core.rules.Verdict;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * M4 self-test ({@code -Pselftest=m4}) on run-server (seed 12345, the tester is op), joined with
 * {@code --quickPlayMultiplayer 127.0.0.1:25565}: table mode, slime chunks unknown without the seed and
 * exact with {@code /spawnsight seed}, and the nether-brick fortress assumption.
 */
final class ServerTest {
	private static final long SEED = 12345L;
	private static BlockPos slimePocket;
	private static BlockPos plainPocket;
	private static BlockPos bricks;
	private static BlockPos rack;

	private ServerTest() {
	}

	static void steps(SelfTest.Script s) {
		s.waitFor("joined run-server", () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.level != null && mc.player != null && mc.getCurrentServer() != null && mc.player.tickCount > 60;
		}, 12000, mc -> mc.player.connection.sendCommand("execute in minecraft:overworld run tp @s 0 140 0")); // includes game start and join
		s.waitFor("in the overworld at 0 140 0", () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.level != null && mc.level.dimension() == Level.OVERWORLD && mc.player != null
					&& mc.player.blockPosition().distManhattan(new net.minecraft.core.Vec3i(0, 140, 0)) < 4 && mc.level.hasChunk(0, 0);
		}, 1200, mc -> {
			SelfTest.check(!mc.hasSingleplayerServer(), "playing on a server, not singleplayer");
			forgetSeed(mc);
			SpawnsightClient.config.overlay = true;
			SpawnsightClient.config.radius = 16;
			SpawnsightClient.config.mobFilter = null;
			// a slime chunk and a normal chunk near the player, by the core formula for the server's seed
			int cx = mc.player.blockPosition().getX() >> 4;
			int cz = mc.player.blockPosition().getZ() >> 4;
			for (int r = 0; r <= 3 && (slimePocket == null || plainPocket == null); r++) { // run-server view distance is 4
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						BlockPos centre = new BlockPos(((cx + dx) << 4) + 8, 19, ((cz + dz) << 4) + 8);
						if (SlimeChunks.isSlimeChunk(SEED, cx + dx, cz + dz)) {
							if (slimePocket == null) {
								slimePocket = centre;
							}
						} else if (plainPocket == null && r >= 1) {
							plainPocket = centre;
						}
					}
				}
			}
			SelfTest.check(slimePocket != null && plainPocket != null, "found a slime chunk and a normal chunk near the player");
			pocket(mc, slimePocket, "stone");
			pocket(mc, plainPocket, "stone");
		});
		s.waitFor("pockets arrive", () -> built(slimePocket, "stone") && built(plainPocket, "stone"), 600, mc -> {
		});
		s.step("light settles", 20, mc -> {
			Why.Result a = Why.evaluate(mc, slimePocket);
			SelfTest.check(a.origin() == SectionLists.Origin.TABLE, "server lists come from the vanilla table (" + a.origin() + ")");
			SelfTest.check(slime(a) == Verdict.Status.UNKNOWN, "slime chunk pocket without a seed: slime unknown (" + slime(a) + ")");
			SelfTest.check(slime(Why.evaluate(mc, plainPocket)) == Verdict.Status.UNKNOWN, "normal chunk pocket without a seed: slime unknown");
			try {
				SpawnsightClient.SEEDS.put(SpawnsightClient.serverAddress(mc), SEED);
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
		});
		s.step("seed set", 5, mc -> {
			Why.Result a = Why.evaluate(mc, slimePocket);
			Verdict v = verdict(a, "minecraft:slime");
			SelfTest.check(v != null && v.status() == Verdict.Status.NOW && Math.abs(v.pNow() - 0.1) < 1e-9,
					"with the seed the slime chunk pocket spawns slimes now at 1 in 10 (" + (v == null ? null : v.status() + " " + v.pNow()) + ")");
			SelfTest.check(slime(Why.evaluate(mc, plainPocket)) == Verdict.Status.NEVER, "with the seed the normal chunk pocket has no slimes");
			mc.player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0 120 0");
		});
		s.waitFor("in the nether", () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.level != null && mc.level.dimension() == Level.NETHER && mc.player != null && mc.level.hasChunk(0, 0)
					&& mc.player.blockPosition().getY() > 110;
		}, 1200, mc -> {
			bricks = new BlockPos(6, 100, 6);
			rack = new BlockPos(-6, 100, -6);
			pocket(mc, bricks, "nether_bricks");
			pocket(mc, rack, "netherrack");
		});
		s.waitFor("nether pockets arrive", () -> built(bricks, "nether_bricks") && built(rack, "netherrack"), 600, mc -> {
		});
		s.step("nether light settles", 20, mc -> {
			Why.Result b = Why.evaluate(mc, bricks);
			SpawnsightClient.LOG.info("[selftest] bricks {}: below {}, message {}, hostile {}", bricks, mc.level.getBlockState(bricks.below()),
					b.message(), b.spot() == null ? null : b.spot().hostile().stream().map(v -> v.mob().id() + "=" + v.status()).toList());
			SelfTest.check(verdict(b, "minecraft:blaze") != null && verdict(b, "minecraft:wither_skeleton") != null,
					"nether bricks: the fortress list (blaze, wither skeleton) is assumed");
			Why.Result r = Why.evaluate(mc, rack);
			SelfTest.check(verdict(r, "minecraft:blaze") == null, "netherrack: biome list, no blazes");
			Verdict zp = verdict(b, "minecraft:zombified_piglin");
			SelfTest.check(zp != null && zp.possibleSometime(), "zombified piglins can spawn on the nether bricks");
			forgetSeed(mc);
			mc.player.connection.sendCommand("execute in minecraft:overworld run tp @s 0 140 0");
		});
	}

	/** The client sees the pocket: floor block below the spawn position, air in it. */
	private static boolean built(BlockPos c, String floor) {
		Minecraft mc = Minecraft.getInstance();
		if (c == null || mc.level == null || !mc.level.hasChunk(c.getX() >> 4, c.getZ() >> 4)) {
			return false;
		}
		String below = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(c.below()).getBlock()).getPath();
		return below.equals(floor) && mc.level.getBlockState(c).isAir() && mc.level.getBlockState(c.above(2)).isAir();
	}

	/** A sealed 3×3×3 air pocket with its floor block below {@code centre} (the spawn position). */
	private static void pocket(Minecraft mc, BlockPos c, String block) {
		mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d %s", c.getX() - 2, c.getY() - 1, c.getZ() - 2, c.getX() + 2,
				c.getY() + 3, c.getZ() + 2, block));
		mc.player.connection.sendCommand(String.format("fill %d %d %d %d %d %d air", c.getX() - 1, c.getY(), c.getZ() - 1, c.getX() + 1,
				c.getY() + 2, c.getZ() + 1));
	}

	private static Verdict verdict(Why.Result r, String id) {
		if (r.spot() == null) {
			return null;
		}
		for (Verdict v : r.spot().hostile()) {
			if (v.mob().id().equals(id)) {
				return v;
			}
		}
		return null;
	}

	private static Verdict.Status slime(Why.Result r) {
		Verdict v = verdict(r, "minecraft:slime");
		return v == null ? null : v.status();
	}

	private static void forgetSeed(Minecraft mc) {
		try {
			SpawnsightClient.SEEDS.remove(SpawnsightClient.serverAddress(mc));
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
