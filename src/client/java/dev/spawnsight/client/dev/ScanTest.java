package dev.spawnsight.client.dev;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.client.probe.Envs;
import dev.spawnsight.client.probe.LevelWorld;
import dev.spawnsight.client.probe.Mobs;
import dev.spawnsight.client.scan.Scanner;
import dev.spawnsight.client.source.SectionLists;
import dev.spawnsight.core.rules.Env;
import dev.spawnsight.core.rules.SpotEvaluator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * M2 self-test ({@code -Pselftest=m2}, singleplayer world "SpawnTest"): fill times at radius 16 and 32,
 * client result equals the same evaluation on the server level, a placed torch updates the overlay,
 * time of day changes it.
 */
final class ScanTest {
	private static long fill16;
	private static long fill32;
	private static BlockPos torch;
	private static int torchTick;
	private static final AtomicReference<String> SERVER_RESULT = new AtomicReference<>();
	private static int redAtNight;

	private ScanTest() {
	}

	static void steps(SelfTest.Script s) {
		s.waitFor("singleplayer world loaded", () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.level != null && mc.player != null && mc.hasSingleplayerServer() && mc.player.tickCount > 60;
		}, 3600, mc -> {
			server(mc, "time set midnight");
			server(mc, "weather clear");
			server(mc, "difficulty normal");
			SpawnsightClient.config.overlay = true;
			SpawnsightClient.config.radius = 16;
			SpawnsightClient.config.showPassive = true;
			SpawnsightClient.config.mobFilter = null;
			Scanner.STATS.reset();
		});
		s.step("let time and weather arrive", 40, mc -> Scanner.STATS.reset());
		s.waitFor("radius 16 filled", ScanTest::filled, 2400, mc -> {
			fill16 = Scanner.STATS.lastFillNanos;
			log(mc, "radius 16");
			SpawnsightClient.config.radius = 32;
			Scanner.STATS.reset();
		});
		s.waitFor("radius 32 filled", ScanTest::filled, 4800, mc -> {
			fill32 = Scanner.STATS.lastFillNanos;
			log(mc, "radius 32");
			// CPU time, averaged: Windows thread CPU time moves in 15.6 ms steps, and wall time also counts other
			// processes and GC on a busy machine (both logged above)
			Scanner.Stats st = Scanner.STATS;
			double avgCpu = st.workTicks == 0 ? 0 : st.cpuNanos / 1e6 / st.workTicks;
			SelfTest.check(avgCpu <= SpawnsightClient.config.scanBudgetMs + 1.0, String.format(Locale.ROOT,
					"scanner stays within the %d ms budget: %.2f ms CPU per working tick over %d ticks", SpawnsightClient.config.scanBudgetMs, avgCpu,
					st.workTicks));
			SelfTest.check(st.cpuNanos < 1_500_000_000L, String.format(Locale.ROOT, "radius 32 fill costs %d ms CPU in total (limit 1500)",
					st.cpuNanos / 1_000_000));
			redAtNight = Scanner.shadeCounts()[SpotEvaluator.Shade.HOSTILE_NOW.ordinal()];
			SelfTest.check(redAtNight > 0, "some blocks are red at midnight (" + redAtNight + ")");
			compareWithServer(mc);
		});
		s.waitFor("server comparison", () -> SERVER_RESULT.get() != null, 1200, mc -> {
			String r = SERVER_RESULT.get();
			SelfTest.check(r.startsWith("0 differ"), "client overlay equals the same rules on the server level: " + r);
			torch = findRed(mc);
			SelfTest.check(torch != null, "a red block near the player for the torch test");
			if (torch != null) {
				server(mc, "setblock " + torch.getX() + " " + torch.getY() + " " + torch.getZ() + " torch");
				torchTick = mc.player.tickCount;
			}
		});
		s.waitFor("torch removes the red", () -> torch == null || shadeAt(torch) != SpotEvaluator.Shade.HOSTILE_NOW.ordinal()
				&& shadeAt(torch.east()) != SpotEvaluator.Shade.HOSTILE_NOW.ordinal(), 400, mc -> {
			if (torch != null) {
				int ticks = mc.player.tickCount - torchTick;
				SelfTest.check(true, "torch at " + torch.toShortString() + ": overlay updated after " + ticks + " ticks (block and its lit neighbour)");
			}
			server(mc, "time set noon");
		});
		s.step("noon arrives", 40, mc -> Scanner.STATS.reset());
		s.waitFor("noon filled", ScanTest::filled, 4800, mc -> {
			int red = Scanner.shadeCounts()[SpotEvaluator.Shade.HOSTILE_NOW.ordinal()];
			int yellow = Scanner.shadeCounts()[SpotEvaluator.Shade.HOSTILE_LATER.ordinal()];
			SelfTest.check(red < redAtNight, "fewer red blocks at noon (" + red + ") than at midnight (" + redAtNight + "), yellow " + yellow);
			log(mc, "env change rescan at radius 32");
			SpawnsightClient.LOG.info("[selftest] fill times: radius 16 {} ms, radius 32 {} ms", fill16 / 1_000_000, fill32 / 1_000_000);
		});
	}

	private static boolean filled() {
		return Scanner.STATS.lastFillNanos >= 0 && Scanner.STATS.queue == 0;
	}

	private static int shadeAt(BlockPos p) {
		Scanner.Section sec = Scanner.section(SectionPos.asLong(p));
		return sec == null ? -1 : sec.shadeAt(SectionLists.index(p.getX() & 15, p.getY() & 15, p.getZ() & 15));
	}

	private static BlockPos findRed(Minecraft mc) {
		BlockPos c = mc.player.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (var e : Scanner.sections().long2ObjectEntrySet()) {
			Scanner.Section sec = e.getValue();
			if (sec.empty()) {
				continue;
			}
			long k = e.getLongKey();
			for (int i = 0; i < 4096; i++) {
				if (sec.shadeAt(i) == SpotEvaluator.Shade.HOSTILE_NOW.ordinal()) {
					BlockPos p = new BlockPos((SectionPos.x(k) << 4) + (i & 15), (SectionPos.y(k) << 4) + (i >> 8), (SectionPos.z(k) << 4) + ((i >> 4) & 15));
					double d = p.distSqr(c);
					// the east neighbour must be red too, to see the light spread
					if (d < bestD && d > 9 && shadeAt(p.east()) == SpotEvaluator.Shade.HOSTILE_NOW.ordinal()) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		return best;
	}

	private static void log(Minecraft mc, String what) {
		Scanner.Stats st = Scanner.STATS;
		int[] sh = Scanner.shadeCounts();
		long bytes = 0;
		int nonEmpty = 0;
		for (Scanner.Section s : Scanner.sections().values()) {
			if (!s.empty()) {
				nonEmpty++;
				bytes += 4096;
			}
		}
		SpawnsightClient.LOG.info(String.format(Locale.ROOT,
				"[selftest] %s: fill %d ms (scanner CPU %d ms, max %.2f ms CPU in one tick), %d sections (%d with colour, %d KB shades), computed %d, %d candidates, avg %.3f ms/section,"
						+ " max %.2f ms/section, max tick %.2f ms; red %d yellow %d unknown %d green %d",
				what, st.lastFillNanos / 1_000_000, st.cpuNanos / 1_000_000, st.maxTickCpuNanos / 1e6, Scanner.cachedSections(), nonEmpty, bytes / 1024, st.sectionsComputed, st.candidates,
				st.sectionsComputed == 0 ? 0 : st.nanosComputing / 1e6 / st.sectionsComputed, st.maxSectionNanos / 1e6, st.maxTickNanos / 1e6,
				sh[4], sh[3], sh[2], sh[1]));
	}

	/** Evaluates every cached candidate again on the server thread, with the server level, and compares. */
	private static void compareWithServer(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		var dim = mc.level.dimension();
		Env clientEnv = Scanner.env();
		List<long[]> work = new ArrayList<>(); // {section key, index, client shade}
		for (var e : Scanner.sections().long2ObjectEntrySet()) {
			Scanner.Section sec = e.getValue();
			SectionLists lists = Scanner.server().get(e.getLongKey(), new java.util.BitSet());
			if (lists == null) {
				continue;
			}
			for (var idx : lists.byIndex().keySet()) {
				work.add(new long[] {e.getLongKey(), idx, sec.shadeAt(idx)});
			}
		}
		server.execute(() -> {
			ServerLevel level = server.getLevel(dim);
			LevelWorld w = new LevelWorld(level, level.getSeed());
			Env env = Envs.of(level, Scanner.table(), Scanner.server().surfaceSlimeChance());
			int differ = 0;
			StringBuilder first = new StringBuilder();
			for (long[] job : work) {
				long k = job[0];
				int i = (int) job[1];
				SectionLists lists = Scanner.server().get(k, new java.util.BitSet());
				int x = (SectionPos.x(k) << 4) + (i & 15);
				int y = (SectionPos.y(k) << 4) + (i >> 8);
				int z = (SectionPos.z(k) << 4) + ((i >> 4) & 15);
				SpotEvaluator.Spot spot = SpotEvaluator.evaluate(w, env, (a, b, c, cat) -> lists.at(i, cat), Mobs::spec, true, null, x, y, z);
				if (spot.shade().ordinal() != job[2]) {
					if (differ++ < 5) {
						first.append(String.format(Locale.ROOT, " [%d %d %d client %d server %d]", x, y, z, job[2], spot.shade().ordinal()));
					}
				}
			}
			SERVER_RESULT.set(differ + " differ of " + work.size() + " candidates; env equal " + env.equals(clientEnv)
					+ (env.equals(clientEnv) ? "" : " (client " + clientEnv + " server " + env + ")") + first);
		});
	}

	private static void server(Minecraft mc, String command) {
		IntegratedServer server = mc.getSingleplayerServer();
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
	}
}
