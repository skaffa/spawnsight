package dev.spawnsight.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * World seeds the player entered for servers ({@code /spawnsight seed}), stored as
 * {@code config/spawnsight/seeds.json}: server address → seed. Used only for slime chunks.
 * Minecraft-free so it can be unit tested.
 */
public final class SeedStore {
	public static final String FILE_NAME = "seeds.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path dir;
	private final Map<String, Long> seeds = new TreeMap<>();

	public SeedStore(Path dir) {
		this.dir = dir;
	}

	public Path file() {
		return dir.resolve(FILE_NAME);
	}

	/** Normalized key for a server address: lower case, no default port. */
	public static String key(String address) {
		String a = address.trim().toLowerCase(Locale.ROOT);
		return a.endsWith(":25565") ? a.substring(0, a.length() - 6) : a;
	}

	/**
	 * Parses a seed the way the world creation screen does: a number is used as is, any other text is
	 * hashed with {@link String#hashCode()}. Returns null for blank input.
	 */
	public static Long parse(String text) {
		String t = text.trim();
		if (t.isEmpty()) {
			return null;
		}
		try {
			return Long.parseLong(t);
		} catch (NumberFormatException e) {
			return (long) t.hashCode();
		}
	}

	public Long get(String address) {
		return seeds.get(key(address));
	}

	public void put(String address, long seed) throws IOException {
		seeds.put(key(address), seed);
		save();
	}

	public boolean remove(String address) throws IOException {
		boolean had = seeds.remove(key(address)) != null;
		if (had) {
			save();
		}
		return had;
	}

	/** Loads the file; a missing or broken file gives an empty store (a broken one is kept as .broken). */
	public void load() throws IOException {
		seeds.clear();
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			Map<String, Long> m = GSON.fromJson(r, new TypeToken<Map<String, Long>>() {
			}.getType());
			if (m != null) {
				m.forEach((k, v) -> {
					if (k != null && v != null) {
						seeds.put(key(k), v);
					}
				});
			}
		} catch (JsonParseException | IllegalStateException e) {
			Files.move(f, dir.resolve(FILE_NAME + ".broken"), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void save() throws IOException {
		Files.createDirectories(dir);
		Path tmp = dir.resolve(FILE_NAME + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(seeds, w);
		}
		Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
	}
}
