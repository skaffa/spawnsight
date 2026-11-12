package dev.spawnsight.core.rules;

import static dev.spawnsight.core.rules.FakeWorld.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.spawnsight.core.rules.Env.Scenario;
import dev.spawnsight.core.rules.Verdict.Status;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Rule chain on a fake grid: the cases a spawn-proofer or farm builder meets (PLAN.md §10). */
class RuleEngineTest {
	static final Scenario DAY = new Scenario("now", 0, false);
	static final Scenario NIGHT = new Scenario("now", 11, false);

	static Env overworld(Scenario now) {
		return new Env(Dimension.OVERWORLD, false, 63, now, Env.OVERWORLD_LATER, 0.5F, 0.5F);
	}

	static Env nether() {
		return new Env(Dimension.NETHER, false, 32, new Scenario("now", 11, false), List.of(), 0, 0);
	}

	static Env end() {
		return new Env(Dimension.END, false, 0, new Scenario("now", 0, false), List.of(), 0, 0);
	}

	/** A stone floor at y = 0 with air above, 9×9, dark. */
	static FakeWorld floor(B floor) {
		FakeWorld w = new FakeWorld();
		w.fill(-4, 0, -4, 4, 0, 4, STONE);
		w.set(0, 0, 0, floor);
		return w;
	}

	static Verdict eval(FakeWorld w, Env e, MobSpec m) {
		return RuleEngine.evaluate(w, e, m, 0, 1, 0);
	}

	// ---- floors ------------------------------------------------------------------------------

	@Test
	void darkStoneFloorSpawnsZombieNowAtFullChance() {
		Verdict v = eval(floor(STONE), overworld(NIGHT), Mobs.ZOMBIE);
		assertEquals(Status.NOW, v.status());
		assertEquals(1.0, v.pNow(), 1e-9);
	}

	@Test
	void bottomSlabIsNotASpawnFloor() {
		Verdict v = eval(floor(BOTTOM_SLAB), overworld(NIGHT), Mobs.ZOMBIE);
		assertEquals(Status.NEVER, v.status());
		assertTrue(v.reason().contains("top face is not solid"), v.reason());
	}

	@Test
	void topSlabIsASpawnFloor() {
		assertEquals(Status.NOW, eval(floor(TOP_SLAB), overworld(NIGHT), Mobs.ZOMBIE).status());
	}

	@Test
	void glassIsNeverASpawnFloor() {
		Verdict v = eval(floor(GLASS), overworld(NIGHT), Mobs.ZOMBIE);
		assertEquals(Status.NEVER, v.status());
		assertTrue(v.reason().contains("never a spawn floor"), v.reason());
	}

	@Test
	void glowstoneEmitsTooMuchLight() {
		Verdict v = eval(floor(GLOWSTONE), overworld(NIGHT), Mobs.ZOMBIE);
		assertTrue(v.reason().contains("light 14 or more"), v.reason());
	}

	@Test
	void carpetInTheFeetBlockBlocksTheSpawnBox() {
		FakeWorld w = floor(STONE).set(0, 1, 0, CARPET);
		Verdict v = eval(w, overworld(NIGHT), Mobs.ZOMBIE);
		assertEquals(Status.NEVER, v.status());
		assertTrue(v.reason().contains("no room"), v.reason());
	}

	@Test
	void standingOnACarpetIsNotAFloor() {
		FakeWorld w = floor(STONE).set(0, 1, 0, CARPET);
		Verdict v = RuleEngine.evaluate(w, overworld(NIGHT), Mobs.ZOMBIE, 0, 2, 0);
		assertTrue(v.reason().contains("top face is not solid"), v.reason());
	}

	@Test
	void railsAndButtonsPreventSpawningInside() {
		assertTrue(eval(floor(STONE).set(0, 1, 0, RAIL), overworld(NIGHT), Mobs.ZOMBIE).reason().contains("rails"));
		assertTrue(eval(floor(STONE).set(0, 1, 0, BUTTON), overworld(NIGHT), Mobs.ZOMBIE).reason().contains("redstone component"));
	}

	@Test
	void waterInTheFeetBlock() {
		Verdict v = eval(floor(STONE).set(0, 1, 0, WATER), overworld(NIGHT), Mobs.ZOMBIE);
		assertTrue(v.reason().contains("fluid"), v.reason());
	}

	// ---- space and hitbox -----------------------------------------------------------------------

	@Test
	void oneBlockGapIsTooLowEvenForSpiders() {
		FakeWorld w = floor(STONE).fill(-4, 2, -4, 4, 2, 4, STONE);
		Verdict v = eval(w, overworld(NIGHT), Mobs.SPIDER);
		assertEquals(Status.NEVER, v.status());
		assertTrue(v.reason().contains("only 1 block of space"), v.reason());
	}

	@Test
	void twoHighGapFitsZombieButNotEnderman() {
		FakeWorld w = floor(STONE).fill(-4, 3, -4, 4, 3, 4, STONE);
		assertEquals(Status.NOW, eval(w, overworld(NIGHT), Mobs.ZOMBIE).status());
		Verdict v = eval(w, overworld(NIGHT), Mobs.ENDERMAN);
		assertEquals(Status.NEVER, v.status());
		assertTrue(v.reason().contains("no room for the enderman"), v.reason());
	}

	@Test
	void threeHighGapFitsEnderman() {
		FakeWorld w = floor(STONE).fill(-4, 4, -4, 4, 4, 4, STONE);
		assertEquals(Status.NOW, eval(w, overworld(NIGHT), Mobs.ENDERMAN).status());
	}

	@Test
	void oneWideCorridorStopsSpidersButNotZombies() {
		FakeWorld w = floor(STONE);
		w.fill(-1, 1, -4, -1, 3, 4, STONE).fill(1, 1, -4, 1, 3, 4, STONE);
		assertEquals(Status.NOW, eval(w, overworld(NIGHT), Mobs.ZOMBIE).status());
		Verdict v = eval(w, overworld(NIGHT), Mobs.SPIDER);
		assertTrue(v.reason().contains("no room for the spider"), v.reason());
	}

	@Test
	void slimeNeedsItsScaledSpawnBox() {
		FakeWorld w = floor(STONE).fill(-4, 3, -4, 4, 3, 4, STONE); // 2 high
		w.slimeChunk = true;
		Verdict v = RuleEngine.evaluate(w, overworld(NIGHT), Mobs.SLIME, 0, 1, 0);
		assertTrue(v.reason().contains("no room for the slime"), v.reason());
		FakeWorld open = floor(STONE);
		open.slimeChunk = true;
		assertEquals(Status.NOW, RuleEngine.evaluate(open, overworld(NIGHT), Mobs.SLIME, 0, 1, 0).status());
	}

	// ---- light ----------------------------------------------------------------------------------

	@Test
	void blockLightOneStopsMonsters() {
		FakeWorld w = floor(STONE).light(0, 1, 0, 0, 1);
		Verdict v = eval(w, overworld(NIGHT), Mobs.ZOMBIE);
		assertEquals(Status.NEVER, v.status());
		assertTrue(v.reason().contains("block light 1 > 0"), v.reason());
	}

	@Test
	void openSkyByDayIsLaterWithTheVanillaChance() {
		FakeWorld w = floor(STONE);
		w.defaultSky = 15;
		Verdict v = eval(w, overworld(DAY), Mobs.ZOMBIE);
		assertEquals(Status.LATER, v.status());
		assertEquals(0.0, v.pNow());
		// night: sky 15 ≤ nextInt(32) → 17/32, light 15 − 11 = 4 ≤ uniform(0..7) → 4/8
		assertEquals(17 / 32.0 * 4 / 8.0, v.pBest(), 1e-9);
		assertEquals("at night", v.bestWhen());
	}

	@Test
	void huskNeedsSky() {
		FakeWorld w = floor(STONE).fill(-4, 5, -4, 4, 5, 4, STONE);
		assertTrue(eval(w, overworld(NIGHT), Mobs.HUSK).reason().contains("open sky"));
	}

	@Test
	void pillagerAllowsBlockLightUpToEight() {
		assertEquals(Status.NOW, eval(floor(STONE).light(0, 1, 0, 15, 8), overworld(DAY), Mobs.PILLAGER).status());
		assertEquals(Status.NEVER, eval(floor(STONE).light(0, 1, 0, 15, 9), overworld(DAY), Mobs.PILLAGER).status());
	}

	// ---- animals and odd mixes ------------------------------------------------------------------

	@Test
	void cowNeedsGrassAndDaylight() {
		FakeWorld lit = floor(GRASS);
		lit.defaultSky = 15;
		assertEquals(Status.NOW, eval(lit, overworld(NIGHT), Mobs.COW).status(), "sky light at noon counts, not the time");
		assertTrue(eval(floor(STONE), overworld(DAY), Mobs.COW).reason().contains("animals_spawnable_on"));
		assertTrue(eval(floor(GRASS), overworld(DAY), Mobs.COW).reason().contains("too dark"));
	}

	@Test
	void goatOffGrassNeedsBrightLightAtSpawnTime() {
		FakeWorld w = floor(goatStone());
		w.defaultSky = 15;
		Verdict v = eval(w, overworld(NIGHT), Mobs.GOAT);
		assertEquals(Status.LATER, v.status(), v.reason());
	}

	private static B goatStone() {
		B b = FakeWorld.solid("minecraft:stone");
		b.tag("#minecraft:goats_spawnable_on");
		return b;
	}

	@Test
	void zombieHorseOnlyOnGrass() {
		FakeWorld open = floor(GRASS);
		open.defaultSky = 15;
		assertEquals(Status.LATER, eval(open, overworld(DAY), Mobs.ZOMBIE_HORSE).status());
		FakeWorld stone = floor(STONE);
		stone.defaultSky = 15;
		Verdict v = eval(stone, overworld(DAY), Mobs.ZOMBIE_HORSE);
		assertEquals(Status.NEVER, v.status());
		assertTrue(v.reason().startsWith("never all at once"), v.reason());
	}

	@Test
	void camelHuskNeverOnSand() {
		FakeWorld w = floor(SAND);
		w.defaultSky = 15;
		assertEquals(Status.NEVER, eval(w, overworld(DAY), Mobs.CAMEL_HUSK).status());
	}

	@Test
	void batsOnlyUndergroundAndDark() {
		FakeWorld cave = floor(batStone()).fill(-4, 3, -4, 4, 3, 4, STONE);
		Verdict v = eval(cave, overworld(DAY), Mobs.BAT);
		assertEquals(Status.NOW, v.status(), v.reason());
		assertEquals(0.5, v.pNow(), 1e-9);
		FakeWorld open = floor(batStone());
		assertTrue(eval(open, overworld(NIGHT), Mobs.BAT).reason().contains("below the surface"));
	}

	private static B batStone() {
		B b = FakeWorld.solid("minecraft:stone");
		b.tag("#minecraft:bats_spawnable_on");
		return b;
	}

	// ---- slimes ---------------------------------------------------------------------------------

	@Test
	void slimeChunkUnknownWithoutSeed() {
		FakeWorld w = floor(STONE);
		w.slimeChunk = null;
		Verdict v = eval(w, overworld(NIGHT), Mobs.SLIME);
		assertEquals(Status.UNKNOWN, v.status());
		assertTrue(v.reason().contains("seed"), v.reason());
	}

	@Test
	void slimeChunkIgnoresLight() {
		FakeWorld w = floor(STONE).light(0, 1, 0, 15, 15);
		w.slimeChunk = true;
		Verdict v = eval(w, overworld(DAY), Mobs.SLIME);
		assertEquals(Status.NOW, v.status());
		assertEquals(0.1, v.pNow(), 1e-9);
	}

	@Test
	void slimeChunkOnlyBelowForty() {
		FakeWorld w = floor(STONE);
		w.slimeChunk = true;
		Verdict v = RuleEngine.evaluate(w.fill(-4, 40, -4, 4, 40, 4, STONE), overworld(NIGHT), Mobs.SLIME, 0, 41, 0);
		assertEquals(Status.NEVER, v.status());
	}

	@Test
	void swampSlimesFollowTheMoon() {
		FakeWorld w = new FakeWorld().fill(-4, 59, -4, 4, 59, 4, STONE);
		w.biomeTags.add("#minecraft:allows_surface_slime_spawns");
		Env newMoon = new Env(Dimension.OVERWORLD, false, 63, NIGHT, Env.OVERWORLD_LATER, 0.0F, 0.5F);
		Verdict v = RuleEngine.evaluate(w, newMoon, Mobs.SLIME, 0, 60, 0);
		assertEquals(Status.LATER, v.status());
		assertEquals(0.5, v.pBest(), 1e-9);
	}

	// ---- water ----------------------------------------------------------------------------------

	@Test
	void drownedDeepOrInRivers() {
		FakeWorld w = new FakeWorld().fill(-2, 30, -2, 2, 50, 2, WATER);
		Verdict deep = RuleEngine.evaluate(w, overworld(NIGHT), Mobs.DROWNED, 0, 40, 0);
		assertEquals(Status.NOW, deep.status(), deep.reason());
		assertEquals(1 / 40.0, deep.pNow(), 1e-9);
		Verdict high = RuleEngine.evaluate(w, overworld(NIGHT), Mobs.DROWNED, 0, 58, 0);
		assertEquals(Status.NEVER, high.status());
	}

	@Test
	void squidNearTheSurfaceOnly() {
		FakeWorld w = new FakeWorld().fill(-2, 40, -2, 2, 63, 2, WATER);
		assertEquals(Status.NOW, RuleEngine.evaluate(w, overworld(DAY), Mobs.SQUID, 0, 55, 0).status());
		assertTrue(RuleEngine.evaluate(w, overworld(DAY), Mobs.SQUID, 0, 45, 0).reason().contains("y 50 to 63"));
	}

	// ---- other dimensions -----------------------------------------------------------------------

	@Test
	void netherIgnoresBlockLightUpToSeven() {
		FakeWorld w = floor(NETHERRACK);
		w.set(0, 0, 0, NETHERRACK).light(0, 1, 0, 0, 7);
		assertEquals(Status.NOW, eval(w, nether(), Mobs.ZOMBIE).status());
		w.light(0, 1, 0, 0, 8);
		assertEquals(Status.NEVER, eval(w, nether(), Mobs.ZOMBIE).status());
	}

	@Test
	void piglinsNotOnWartBlocks() {
		FakeWorld w = floor(WART);
		assertTrue(eval(w, nether(), Mobs.PIGLIN).reason().contains("nether wart"));
		assertTrue(eval(w, nether(), Mobs.ZOMBIFIED_PIGLIN).reason().contains("nether wart"));
	}

	@Test
	void endermanIgnoresEndWalkLimitButZombieDoesNot() {
		FakeWorld w = floor(STONE);
		w.defaultSky = 11; // end sky, darkening 0
		assertEquals(Status.NOW, eval(w, end(), Mobs.ENDERMAN).status());
		Verdict v = eval(w, end(), Mobs.ZOMBIE);
		assertEquals(Status.NEVER, v.status(), "light 11 > 10: too bright to stay in the end");
	}

	@Test
	void peacefulStopsMonsters() {
		Env peaceful = new Env(Dimension.OVERWORLD, true, 63, NIGHT, Env.OVERWORLD_LATER, 0.5F, 0.5F);
		assertTrue(eval(floor(STONE), peaceful, Mobs.ZOMBIE).reason().contains("peaceful"));
	}
}
