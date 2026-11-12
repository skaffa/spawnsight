package dev.spawnsight.client.probe;

import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.rules.MobSpec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** {@link MobSpec}s read from the real {@link EntityType}s, cached by id. Thread-safe. */
public final class Mobs {
	private static final Map<String, EntityType<?>> TYPES = new ConcurrentHashMap<>();
	private static final Map<String, MobSpec> SPECS = new ConcurrentHashMap<>();
	private static final MobSpec MISSING = new MobSpec("", Category.MISC, 0, 0, 0, 0, false, false, false, false);

	private Mobs() {
	}

	/** The entity type for an id, or null when it is not registered. */
	public static EntityType<?> type(String id) {
		return TYPES.computeIfAbsent(id, k -> BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(k)).orElse(null));
	}

	/** The spec for an id, or null when the type is not registered. */
	public static MobSpec spec(String id) {
		MobSpec s = SPECS.computeIfAbsent(id, k -> {
			EntityType<?> t = type(k);
			return t == null ? MISSING : of(k, t);
		});
		return s == MISSING ? null : s;
	}

	private static MobSpec of(String id, EntityType<?> t) {
		AABB spawn = t.getSpawnAABB(0, 0, 0);
		EntityDimensions d = t.getDimensions();
		return new MobSpec(id, Category.valueOf(t.getCategory().name()), spawn.getXsize(), spawn.getYsize(), d.width(), d.height(),
				t.fireImmune(), t.isAllowedInPeaceful(), t.canSpawnFarFromPlayer(), t.canSummon());
	}
}
