package dev.spawnsight.core.rules;

import static dev.spawnsight.core.rules.FakeWorld.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.spawnsight.core.rules.SpotEvaluator.Shade;
import dev.spawnsight.core.rules.SpotEvaluator.Spot;
import dev.spawnsight.core.table.SpawnEntry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class SpotEvaluatorTest {
	private static final Map<String, MobSpec> SPECS = Map.of(
			"minecraft:zombie", Mobs.ZOMBIE, "minecraft:spider", Mobs.SPIDER, "minecraft:enderman", Mobs.ENDERMAN,
			"minecraft:slime", Mobs.SLIME, "minecraft:cow", Mobs.COW);

	private static final SpotEvaluator.Lists PLAINS = (x, y, z, c) -> switch (c) {
		case MONSTER -> List.of(new SpawnEntry("minecraft:spider", 100, 4, 4), new SpawnEntry("minecraft:zombie", 95, 4, 4),
				new SpawnEntry("minecraft:slime", 100, 4, 4), new SpawnEntry("minecraft:enderman", 10, 1, 4),
				new SpawnEntry("minecraft:modded_thing", 5, 1, 1));
		case CREATURE -> List.of(new SpawnEntry("minecraft:cow", 8, 4, 4));
		default -> List.of();
	};

	private static Spot eval(FakeWorld w, Env e, boolean passive, String filter) {
		return SpotEvaluator.evaluate(w, e, PLAINS, SPECS::get, passive, filter, 0, 1, 0);
	}

	@Test
	void darkRoomIsRed() {
		FakeWorld w = RuleEngineTest.floor(STONE);
		Spot s = eval(w, RuleEngineTest.overworld(RuleEngineTest.NIGHT), false, null);
		assertEquals(Shade.HOSTILE_NOW, s.shade());
		assertEquals(4, s.hostile().size(), "unknown entity ids are skipped");
	}

	@Test
	void openFieldByDayIsYellow() {
		FakeWorld w = RuleEngineTest.floor(GRASS);
		w.defaultSky = 15;
		Spot s = eval(w, RuleEngineTest.overworld(RuleEngineTest.DAY), true, null);
		assertEquals(Shade.HOSTILE_LATER, s.shade());
		assertTrue(s.passivePossible());
	}

	@Test
	void litGrassIsGreenOnly() {
		FakeWorld w = RuleEngineTest.floor(GRASS);
		w.defaultSky = 15;
		w.defaultBlock = 14;
		assertEquals(Shade.PASSIVE, eval(w, RuleEngineTest.overworld(RuleEngineTest.DAY), true, null).shade());
		assertEquals(Shade.NONE, eval(w, RuleEngineTest.overworld(RuleEngineTest.DAY), false, null).shade());
	}

	@Test
	void unknownSlimeChunkShowsWhenNothingElseCan() {
		FakeWorld w = RuleEngineTest.floor(STONE).light(0, 1, 0, 0, 15);
		w.slimeChunk = null;
		assertEquals(Shade.UNKNOWN, eval(w, RuleEngineTest.overworld(RuleEngineTest.NIGHT), false, null).shade());
	}

	@Test
	void farmFilterKeepsOneMob() {
		FakeWorld w = RuleEngineTest.floor(STONE).fill(-4, 3, -4, 4, 3, 4, STONE); // 2 high: no endermen
		Spot s = eval(w, RuleEngineTest.overworld(RuleEngineTest.NIGHT), false, "minecraft:enderman");
		assertEquals(1, s.hostile().size());
		assertEquals(Shade.NONE, s.shade());
	}
}
