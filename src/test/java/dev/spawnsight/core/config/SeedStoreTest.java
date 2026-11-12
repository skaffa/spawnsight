package dev.spawnsight.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class SeedStoreTest {
	@TempDir
	Path tmp;

	@Test
	void parseLikeWorldCreation() {
		assertEquals(12345L, SeedStore.parse("12345"));
		assertEquals(-7L, SeedStore.parse(" -7 "));
		assertEquals((long) "hello".hashCode(), SeedStore.parse("hello"));
		assertNull(SeedStore.parse("  "));
	}

	@Test
	void keysIgnoreCaseAndDefaultPort() {
		assertEquals("play.example.net", SeedStore.key("Play.Example.NET:25565"));
		assertEquals("127.0.0.1:25566", SeedStore.key("127.0.0.1:25566"));
	}

	@Test
	void roundTripAndRemove() throws Exception {
		SeedStore s = new SeedStore(tmp.resolve("spawnsight"));
		s.put("127.0.0.1:25565", 12345L);
		SeedStore back = new SeedStore(tmp.resolve("spawnsight"));
		back.load();
		assertEquals(12345L, back.get("127.0.0.1"));
		assertTrue(back.remove("127.0.0.1"));
		assertNull(back.get("127.0.0.1"));
		assertFalse(back.remove("127.0.0.1"));
	}

	@Test
	void brokenFileIsSetAside() throws Exception {
		Path dir = tmp.resolve("spawnsight");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(SeedStore.FILE_NAME), "{ nope", StandardCharsets.UTF_8);
		SeedStore s = new SeedStore(dir);
		s.load();
		assertNull(s.get("x"));
		assertTrue(Files.exists(dir.resolve(SeedStore.FILE_NAME + ".broken")));
	}
}
