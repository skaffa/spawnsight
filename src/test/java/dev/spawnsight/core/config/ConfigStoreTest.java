package dev.spawnsight.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class ConfigStoreTest {
	@TempDir
	Path tmp;

	private ConfigStore store() {
		Clock fixed = Clock.fixed(Instant.parse("2026-11-12T10:20:30Z"), ZoneOffset.UTC);
		return new ConfigStore(tmp.resolve("spawnsight"), fixed);
	}

	@Test
	void missingFileGivesDefaultsAndCreatesFolder() throws Exception {
		ConfigStore s = store();
		SpawnsightConfig c = s.load();
		assertEquals(24, c.radius);
		assertFalse(c.overlay);
		assertTrue(Files.isRegularFile(tmp.resolve("spawnsight").resolve("config.json")));
		assertNull(s.lastBackup());
	}

	@Test
	void roundTrip() throws Exception {
		ConfigStore s = store();
		SpawnsightConfig c = new SpawnsightConfig();
		c.radius = 32;
		c.showPassive = true;
		c.mobFilter = "minecraft:zombie";
		s.save(c);
		SpawnsightConfig back = s.load();
		assertEquals(32, back.radius);
		assertTrue(back.showPassive);
		assertEquals("minecraft:zombie", back.mobFilter);
	}

	@Test
	void outOfRangeValuesAreClamped() throws Exception {
		ConfigStore s = store();
		Files.createDirectories(s.dir());
		Files.writeString(s.file(), "{\"radius\": 500, \"opacity\": 0, \"scanBudgetMs\": 99, \"mobFilter\": \"  \"}", StandardCharsets.UTF_8);
		SpawnsightConfig c = s.load();
		assertEquals(64, c.radius);
		assertEquals(10, c.opacity);
		assertEquals(10, c.scanBudgetMs);
		assertNull(c.mobFilter);
	}

	@Test
	void brokenFileIsBackedUpAndDefaultsUsed() throws Exception {
		ConfigStore s = store();
		Files.createDirectories(s.dir());
		Files.writeString(s.file(), "{ this is not json", StandardCharsets.UTF_8);
		SpawnsightConfig c = s.load();
		assertEquals(24, c.radius);
		Path backup = s.dir().resolve("config.json.broken-20261112-102030");
		assertEquals(backup, s.lastBackup());
		assertEquals("{ this is not json", Files.readString(backup, StandardCharsets.UTF_8));
		assertTrue(Files.isRegularFile(s.file()));
	}

	@Test
	void emptyFileCountsAsBroken() throws Exception {
		ConfigStore s = store();
		Files.createDirectories(s.dir());
		Files.writeString(s.file(), "", StandardCharsets.UTF_8);
		s.load();
		assertNotNull(s.lastBackup());
	}
}
