package dev.spawnsight.client.dev;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.core.rules.MobRule;
import dev.spawnsight.core.rules.MobRules;
import dev.spawnsight.core.rules.SlimeChunks;
import dev.spawnsight.core.table.SpawnEntry;
import dev.spawnsight.core.table.SpawnTable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.AABB;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Dev-only scripted checks, enabled with {@code ./gradlew runClient -Pselftest=<mode>}. Logs every check,
 * then {@code SPAWNSIGHT SELFTEST PASS} or {@code FAIL} and quits.
 * <p>
 * Mode {@code m1} (PLAN.md §11 M1): compares core code with the real 26.3 classes (slime chunks,
 * placement types, spawn table ids) and, on the test server, reads what the client really gets
 * (biome tags, sky darkening per weather, slime size). Start it with
 * {@code --quickPlayMultiplayer 127.0.0.1:25565}; the player must be op.
 */
public final class SelfTest {
	private static final String MODE = System.getProperty("spawnsight.selftest", "");
	private static final List<String> FAILURES = new ArrayList<>();
	private static final List<Step> STEPS = new ArrayList<>();
	private static int index;
	private static int wait;
	private static boolean finished;

	private record Step(String name, int delay, BooleanSupplier until, int timeout, Consumer<Minecraft> action) {
	}

	private SelfTest() {
	}

	public static boolean active() {
		return !MODE.isEmpty();
	}

	public static void init() {
		if (MODE.isEmpty()) {
			return;
		}
		SpawnsightClient.LOG.info("[selftest] mode {}", MODE);
		if (MODE.equals("m1")) {
			m1();
		} else {
			fail("unknown self-test mode " + MODE);
			STEPS.add(new Step("end", 0, () -> true, 1, SelfTest::finish));
		}
	}

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		if (MODE.isEmpty() || finished || index >= STEPS.size()) {
			return;
		}
		Step s = STEPS.get(index);
		if (wait < s.delay) {
			wait++;
			return;
		}
		if (!s.until.getAsBoolean()) {
			if (++wait > s.delay + s.timeout) {
				fail("timed out waiting: " + s.name);
				finish(mc);
			}
			return;
		}
		SpawnsightClient.LOG.info("[selftest] step: {}", s.name);
		try {
			s.action.accept(mc);
		} catch (RuntimeException e) {
			SpawnsightClient.LOG.error("[selftest] step {} threw", s.name, e);
			fail(s.name + " threw " + e);
			finish(mc);
			return;
		}
		index++;
		wait = 0;
	}

	private static void step(String name, int delay, Consumer<Minecraft> action) {
		STEPS.add(new Step(name, delay, () -> true, 1, action));
	}

	private static void waitFor(String name, BooleanSupplier until, int timeout, Consumer<Minecraft> action) {
		STEPS.add(new Step(name, 0, until, timeout, action));
	}

	static void check(boolean ok, String what) {
		if (ok) {
			SpawnsightClient.LOG.info("[selftest] ok   {}", what);
		} else {
			fail(what);
		}
	}

	private static void fail(String what) {
		SpawnsightClient.LOG.error("[selftest] FAIL {}", what);
		FAILURES.add(what);
	}

	private static void finish(Minecraft mc) {
		if (finished) {
			return;
		}
		finished = true;
		if (FAILURES.isEmpty()) {
			SpawnsightClient.LOG.info("SPAWNSIGHT SELFTEST PASS");
		} else {
			SpawnsightClient.LOG.error("SPAWNSIGHT SELFTEST FAIL ({}): {}", FAILURES.size(), FAILURES);
		}
		mc.stop();
	}

	// ---- M1 ---------------------------------------------------------------------------------------

	private static void m1() {
		step("slime chunks match WorldgenRandom", 0, mc -> {
			Random r = new Random(42);
			int bad = 0;
			int n = 20000;
			for (int i = 0; i < n; i++) {
				long seed = i < 100 ? i : r.nextLong();
				int cx = i % 3 == 0 ? r.nextInt(4_000_000) - 2_000_000 : r.nextInt(2000) - 1000;
				int cz = i % 5 == 0 ? r.nextInt(4_000_000) - 2_000_000 : r.nextInt(2000) - 1000;
				boolean vanilla = WorldgenRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) == 0;
				if (vanilla != SlimeChunks.isSlimeChunk(seed, cx, cz)) {
					bad++;
				}
			}
			check(bad == 0, "slime chunk formula equals vanilla for " + n + " (seed, chunk) pairs (" + bad + " differ)");
			StringBuilder ref = new StringBuilder();
			for (int x = 0; x < 16; x++) {
				if (WorldgenRandom.seedSlimeChunk(x, 0, 12345L, 987234911L).nextInt(10) == 0) {
					ref.append(x).append(' ');
				}
			}
			SpawnsightClient.LOG.info("[selftest] reference: slime chunks for seed 12345 at z=0, x 0..15: {}", ref);
		});
		step("placement types match SpawnPlacements", 0, mc -> {
			int bad = 0;
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
				SpawnPlacementType vanilla = SpawnPlacements.getPlacementType(type);
				SpawnPlacementType ours = switch (MobRules.get(id).placement()) {
					case ON_GROUND -> SpawnPlacementTypes.ON_GROUND;
					case IN_WATER -> SpawnPlacementTypes.IN_WATER;
					case IN_LAVA -> SpawnPlacementTypes.IN_LAVA;
					case NO_RESTRICTIONS -> SpawnPlacementTypes.NO_RESTRICTIONS;
				};
				if (vanilla != ours) {
					bad++;
					SpawnsightClient.LOG.warn("[selftest] placement of {}: ours {} vanilla differs", id, MobRules.get(id).placement());
				}
			}
			check(bad == 0, "every entity type has the vanilla placement type (" + bad + " differ)");
		});
		step("spawn table ids are known", 0, mc -> {
			SpawnTable t = readTable();
			int unknown = 0;
			for (Map<String, List<SpawnEntry>> cats : t.biomes.values()) {
				for (List<SpawnEntry> l : cats.values()) {
					for (SpawnEntry e : l) {
						Identifier id = Identifier.parse(e.type());
						if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id) || !MobRules.isKnown(e.type())) {
							if (unknown++ < 5) {
								SpawnsightClient.LOG.warn("[selftest] table entity {} unknown", e.type());
							}
						}
					}
				}
			}
			check(unknown == 0, "every spawn table entity exists and has a rule (" + unknown + " unknown)");
			check(t.biomes.size() == countBiomes(), "table has every built-in biome (" + t.biomes.size() + ")");
		});
		waitFor("joined the test server", () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.level != null && mc.player != null && mc.player.tickCount > 40;
		}, 2400, mc -> {
			var biomes = mc.level.registryAccess().lookupOrThrow(Registries.BIOME);
			var swamp = biomes.get(ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace("swamp")));
			check(swamp.isPresent(), "client knows the swamp biome");
			swamp.ifPresent(h -> {
				boolean tag = h.is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS);
				check(tag, "biome tags reach the client (swamp has #allows_surface_slime_spawns)");
				Biome b = h.value();
				SpawnsightClient.LOG.info("[selftest] client swamp biome object: {}", b);
			});
			check(mc.level.getSeaLevel() == 63, "client sea level 63 (" + mc.level.getSeaLevel() + ")");
			Entity slime = EntityTypes.SLIME.create(mc.level, EntitySpawnReason.NATURAL);
			if (slime != null) {
				AABB spawnBox = EntityTypes.SLIME.getSpawnAABB(0.5, 0, 0.5);
				SpawnsightClient.LOG.info("[selftest] slime: created box {} x {}, spawn box {} x {}", slime.getBbWidth(), slime.getBbHeight(),
						spawnBox.getXsize(), spawnBox.getYsize());
				check(Math.abs(spawnBox.getXsize() - 2.08) < 1e-4, "slime spawn box is 2.08 wide");
			}
			mc.player.connection.sendCommand("time set noon");
			mc.player.connection.sendCommand("weather clear");
		});
		waitFor("clear noon", () -> rain() == 0 && thunder() == 0 && dayTime() > 5000 && dayTime() < 7000, 600,
				mc -> darken("clear noon", mc, 0));
		step("start rain", 0, mc -> mc.player.connection.sendCommand("weather rain"));
		waitFor("full rain", () -> rain() >= 1.0F, 600, mc -> darken("noon, full rain", mc, 3));
		step("start thunder", 0, mc -> mc.player.connection.sendCommand("weather thunder"));
		waitFor("full thunder", () -> thunder() >= 1.0F && rain() >= 1.0F, 600, mc -> {
			darken("noon, full thunder", mc, 5);
			check(mc.level.isThundering(), "client knows it is thundering");
			mc.player.connection.sendCommand("time set midnight");
		});
		waitFor("midnight", () -> dayTime() > 17000 && dayTime() < 19000, 600, mc -> {
			darken("midnight, thunder", mc, 11);
			mc.player.connection.sendCommand("weather clear");
		});
		waitFor("clear midnight", () -> rain() == 0, 600, mc -> darken("clear midnight", mc, 11));
		step("end", 5, SelfTest::finish);
	}

	private static float rain() {
		return Minecraft.getInstance().level.getRainLevel(1.0F);
	}

	private static float thunder() {
		return Minecraft.getInstance().level.getThunderLevel(1.0F);
	}

	private static long dayTime() {
		return Minecraft.getInstance().level.getOverworldClockTime() % 24000L;
	}

	private static void darken(String when, Minecraft mc, int expected) {
		int d = mc.level.getSkyDarken();
		check(d == expected, "sky darkening " + when + ": " + d + " (expected " + expected + ")");
	}

	private static int countBiomes() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? readTable().biomes.size() : (int) mc.level.registryAccess().lookupOrThrow(Registries.BIOME).listElementIds().count();
	}

	private static SpawnTable readTable() {
		try (Reader r = new InputStreamReader(SelfTest.class.getResourceAsStream(SpawnTable.RESOURCE), StandardCharsets.UTF_8)) {
			return SpawnTable.read(r);
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static MobRule rule(String id) {
		return MobRules.get(id);
	}
}
