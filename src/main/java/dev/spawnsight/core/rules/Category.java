package dev.spawnsight.core.rules;

import java.util.Locale;

/** Vanilla 26.3 {@code MobCategory}: name, friendly flag and instant-despawn distance. */
public enum Category {
	MONSTER(false, 128),
	CREATURE(true, 128),
	AMBIENT(true, 128),
	AXOLOTLS(true, 128),
	UNDERGROUND_WATER_CREATURE(true, 128),
	WATER_CREATURE(true, 128),
	WATER_AMBIENT(true, 64),
	MISC(true, 128);

	/** Random despawn starts beyond this distance ({@code MobCategory.noDespawnDistance}, same for all). */
	public static final int NO_DESPAWN_DISTANCE = 32;

	public final boolean friendly;
	public final int despawnDistance;

	Category(boolean friendly, int despawnDistance) {
		this.friendly = friendly;
		this.despawnDistance = despawnDistance;
	}

	/** The serialized name used by data packs, such as {@code "water_ambient"}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Category byId(String id) {
		for (Category c : values()) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}
}
