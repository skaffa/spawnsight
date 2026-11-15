package dev.spawnsight.client.dev;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.client.probe.Envs;
import dev.spawnsight.client.probe.LevelWorld;
import dev.spawnsight.client.probe.Mobs;
import dev.spawnsight.client.scan.Scanner;
import dev.spawnsight.client.source.SectionLists;
import dev.spawnsight.client.source.ServerLists;
import dev.spawnsight.client.source.TableLists;
import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.rules.Env;
import dev.spawnsight.core.rules.MobSpec;
import dev.spawnsight.core.rules.RuleEngine;
import dev.spawnsight.core.rules.SpotEvaluator;
import dev.spawnsight.core.rules.Verdict;
import dev.spawnsight.core.table.SpawnEntry;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * M5 acceptance ({@code -Pselftest=m5}, singleplayer "SpawnTest"):
 * <ol>
 * <li>Every candidate block of the demo rig and every mob in its spawn lists: Spawnsight's chance now
 * against vanilla's own checks ({@code SpawnPlacements.isSpawnPositionOk}, {@code checkSpawnRules} with
 * many real random draws, {@code noCollision(getSpawnAABB)}, {@code Mob.checkSpawnRules} and
 * {@code checkSpawnObstruction} on a created mob), with the world frozen at midnight.</li>
 * <li>Table mode (vanilla table by biome) gives the same shades as the server's lists on the rig.</li>
 * <li>Natural spawning: every mob the server spawns in the rig spawned where Spawnsight said "now".</li>
 * </ol>
 */
final class RigTest {
	private static final int DRAWS = 600;
	private static final int ROUNDS = 10;
	private static BlockPos origin;
	private static int[] box;
	private static final AtomicReference<String> EXACT = new AtomicReference<>();
	private static volatile boolean recording;
	private static final List<String> SPAWNS = new ArrayList<>();
	private static final List<String> BAD_SPAWNS = new ArrayList<>();
	private static int round;
	private static boolean listening;

	private RigTest() {
	}

	static void steps(SelfTest.Script s) {
		s.waitFor("singleplayer world loaded", () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.level != null && mc.player != null && mc.hasSingleplayerServer() && mc.player.tickCount > 60;
		}, 12000, mc -> {
			listen();
			cmd(mc, "gamemode creative SpawnTester");
			cmd(mc, "difficulty normal");
			cmd(mc, "weather clear");
			cmd(mc, "time set midnight");
			SpawnsightClient.config.overlay = true;
			SpawnsightClient.config.mobFilter = null;
			SpawnsightClient.config.radius = 32;
			origin = DemoRig.origin(mc);
			box = DemoRig.bounds(origin);
			DemoRig.build(mc, origin);
			// the player stands 35+ blocks from the rig (no spawning within 24), well inside 128
			int px = origin.getX() + 14;
			int pz = origin.getZ() + 52;
			cmd(mc, String.format("fill %d %d %d %d %d %d stone", px - 1, DemoRig.Y, pz - 1, px + 1, DemoRig.Y, pz + 1));
			cmd(mc, String.format("tp SpawnTester %d.5 %d %d.5 180 20", px, DemoRig.Y + 1, pz));
			cmd(mc, "kill @e[type=!player]");
			cmd(mc, "tick freeze");
		});
		s.waitFor("rig built", () -> {
			Minecraft mc = Minecraft.getInstance();
			return origin != null && mc.level.getBlockState(origin.offset(7, 1 + DemoRig.Y - origin.getY(), 10)).isSolid();
		}, 1200, mc -> {
		});
		s.step("light settles", 60, mc -> {
			IntegratedServer server = mc.getSingleplayerServer();
			var dim = mc.level.dimension();
			server.execute(() -> EXACT.set(exact(server.getLevel(dim))));
		});
		s.waitFor("exact comparison", () -> EXACT.get() != null, 6000, mc -> {
			String r = EXACT.get();
			SpawnsightClient.LOG.info("[selftest] {}", r);
			SelfTest.check(r.contains(" 0 mismatches") && r.contains("table: 0 differ"), "rig: Spawnsight equals vanilla's checks and table mode (" + r + ")");
		});
		for (int i = 0; i < ROUNDS; i++) {
			s.step("natural spawning round", 5, mc -> {
				round++;
				cmd(mc, "kill @e[type=!player]");
				cmd(mc, "time set 18000");
				cmd(mc, "tick unfreeze");
				recording = true;
				cmd(mc, "tick sprint 600");
			});
			s.waitFor("sprint done", () -> {
				IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
				return !server.tickRateManager().isSprinting();
			}, 6000, mc -> {
				recording = false;
				cmd(mc, "tick freeze");
				SpawnsightClient.LOG.info("[selftest] round {}: {} spawns in the rig so far", round, SPAWNS.size());
			});
		}
		s.step("natural spawning verdict", 5, mc -> {
			cmd(mc, "tick unfreeze");
			SpawnsightClient.LOG.info("[selftest] spawns in the rig: {}", SPAWNS);
			SelfTest.check(SPAWNS.size() >= 3, "the server spawned mobs in the rig (" + SPAWNS.size() + ")");
			SelfTest.check(BAD_SPAWNS.isEmpty(), "every natural spawn in the rig was on a block Spawnsight marks as possible now " + BAD_SPAWNS);
		});
	}

	private static void listen() {
		if (listening) {
			return;
		}
		listening = true;
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!recording || box == null || !(entity instanceof Mob) || entity.isPassenger()) {
				return;
			}
			BlockPos p = entity.blockPosition();
			if (p.getX() < box[0] || p.getY() < box[1] || p.getZ() < box[2] || p.getX() > box[3] || p.getY() > box[4] || p.getZ() > box[5]) {
				return;
			}
			String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			Verdict v = verdict(level, p, id);
			String rec = id.replace("minecraft:", "") + "@" + p.toShortString();
			SPAWNS.add(rec);
			if (v == null || v.status() != Verdict.Status.NOW) {
				BAD_SPAWNS.add(rec + " Spawnsight: " + (v == null ? "not in the lists" : v.status() + " " + v.reason()));
			}
		});
	}

	private static Env env(ServerLevel level) {
		return Envs.of(level, Scanner.table(), level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE));
	}

	private static Verdict verdict(ServerLevel level, BlockPos p, String id) {
		List<SpawnEntry>[] lists = ServerLists.listsAt(level, p);
		MobSpec spec = Mobs.spec(id);
		if (spec == null) {
			return null;
		}
		boolean listed = false;
		for (List<SpawnEntry> l : lists) {
			for (SpawnEntry e : l) {
				listed |= e.type().equals(id);
			}
		}
		if (!listed) {
			return null;
		}
		return RuleEngine.evaluate(new LevelWorld(level, level.getSeed()), env(level), spec, p.getX(), p.getY(), p.getZ());
	}

	/** Part 1 and 2, on the server thread. */
	private static String exact(ServerLevel level) {
		LevelWorld w = new LevelWorld(level, level.getSeed());
		Env env = env(level);
		TableLists table = new TableLists(Scanner.table());
		RandomSource rnd = RandomSource.create(20261112L);
		int positions = 0;
		int pairs = 0;
		int possibleNow = 0;
		int mismatches = 0;
		int tableDiffer = 0;
		StringBuilder first = new StringBuilder();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = box[0]; x <= box[3]; x++) {
			for (int y = box[1]; y <= box[4]; y++) {
				for (int z = box[2]; z <= box[5]; z++) {
					p.set(x, y, z);
					BlockState s = level.getBlockState(p);
					BlockState below = level.getBlockState(p.below());
					if (!LevelWorld.candidate(level, p, s, below)) {
						continue;
					}
					positions++;
					BlockPos pos = p.immutable();
					List<SpawnEntry>[] lists = ServerLists.listsAt(level, pos);
					for (Category c : Category.values()) {
						java.util.Set<String> seen = new java.util.HashSet<>();
						for (SpawnEntry e : lists[c.ordinal()]) {
							if (!seen.add(e.type())) {
								continue;
							}
							MobSpec spec = Mobs.spec(e.type());
							if (spec == null) {
								continue;
							}
							pairs++;
							Verdict v = RuleEngine.evaluate(w, env, spec, x, y, z);
							int k = vanillaPasses(level, Mobs.type(e.type()), pos, rnd);
							if (v.pNow() > 0) {
								possibleNow++;
							}
							boolean bad = k > 0 && v.pNow() == 0 || k == 0 && v.pNow() * DRAWS >= 8;
							if (bad) {
								if (mismatches++ < 8) {
									first.append(String.format(Locale.ROOT, " [%s at %d %d %d: vanilla %d/%d, Spawnsight %s %.4f: %s]", spec.shortName(), x, y, z, k,
											DRAWS, v.status(), v.pNow(), v.reason()));
								}
							}
						}
					}
					// table mode: same shade from the vanilla table by biome?
					long section = SectionPos.asLong(pos);
					BitSet one = new BitSet(4096);
					int idx = SectionLists.index(x & 15, y & 15, z & 15);
					one.set(idx);
					SectionLists tl = table.get(level, section, one);
					SpotEvaluator.Shade a = SpotEvaluator.shade(w, env, (xx, yy, zz, cat) -> lists[cat.ordinal()], Mobs::spec, true, null, x, y, z);
					SpotEvaluator.Shade b = SpotEvaluator.shade(w, env, (xx, yy, zz, cat) -> tl.at(idx, cat), Mobs::spec, true, null, x, y, z);
					if (a != b) {
						tableDiffer++;
					}
				}
			}
		}
		return String.format(Locale.ROOT, "rig: %d candidate blocks, %d block/mob pairs (%d possible now), %d draws each: %d mismatches;"
				+ " table: %d differ%s", positions, pairs, possibleNow, DRAWS, mismatches, tableDiffer, first);
	}

	/** How many of {@link #DRAWS} attempts pass vanilla's per-position checks (no player distance, no mob cap). */
	private static int vanillaPasses(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource rnd) {
		if (type.getCategory() == MobCategory.MISC || !type.canSummon()) {
			return 0;
		}
		if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
			return 0;
		}
		if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
			return 0;
		}
		Entity e = type.create(level, EntitySpawnReason.NATURAL);
		if (!(e instanceof Mob mob)) {
			return 0;
		}
		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		if (!mob.checkSpawnRules(level, EntitySpawnReason.NATURAL) || !mob.checkSpawnObstruction(level)) {
			return 0;
		}
		int k = 0;
		for (int i = 0; i < DRAWS; i++) {
			if (SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, rnd)) {
				k++;
			}
		}
		return k;
	}

	private static void cmd(Minecraft mc, String command) {
		DemoRig.cmd(mc, command);
	}
}
