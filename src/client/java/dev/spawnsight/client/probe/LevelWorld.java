package dev.spawnsight.client.probe;

import dev.spawnsight.core.rules.MobSpec;
import dev.spawnsight.core.rules.RuleEngine;
import dev.spawnsight.core.rules.SlimeChunks;
import dev.spawnsight.core.rules.World;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link World} over a real {@link Level}: every method calls the vanilla method the rule is made of.
 * Works on the client level and, in singleplayer, on the server level (self-test).
 * Not thread-safe: use one instance per thread.
 */
public final class LevelWorld implements World {
	private final Level level;
	/** World seed for slime chunks, or null when unknown. */
	private final Long seed;
	private final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
	private final Map<String, TagKey<Block>> blockTags = new HashMap<>();
	private final Map<String, Block> blocks = new HashMap<>();
	private final Map<String, TagKey<Biome>> biomeTags = new HashMap<>();

	public LevelWorld(Level level, Long seed) {
		this.level = level;
		this.seed = seed;
	}

	public Level level() {
		return level;
	}

	private BlockPos at(int x, int y, int z) {
		return p.set(x, y, z);
	}

	private static EntityType<?> type(MobSpec mob) {
		return Mobs.type(mob.id());
	}

	@Override
	public String name(int x, int y, int z) {
		return level.getBlockState(at(x, y, z)).getBlock().getName().getString();
	}

	@Override
	public Floor floor(int x, int y, int z, MobSpec mob) {
		BlockPos pos = at(x, y, z);
		BlockState s = level.getBlockState(pos);
		if (s.isValidSpawn(level, pos, type(mob))) {
			return Floor.OK;
		}
		if (!s.isFaceSturdy(level, pos, Direction.UP)) {
			return Floor.NOT_STURDY;
		}
		return s.getLightEmission() >= 14 ? Floor.EMITS_LIGHT : Floor.BLOCK_RULE;
	}

	/** Same order as {@code NaturalSpawner.isValidEmptySpawnBlock}. */
	@Override
	public Space space(int x, int y, int z, MobSpec mob) {
		BlockPos pos = at(x, y, z);
		BlockState s = level.getBlockState(pos);
		if (s.isCollisionShapeFullBlock(level, pos)) {
			return Space.FULL_BLOCK;
		}
		if (s.isSignalSource()) {
			return Space.SIGNAL_SOURCE;
		}
		if (!s.getFluidState().isEmpty()) {
			return Space.FLUID;
		}
		if (s.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE)) {
			return Space.NO_SPAWN_INSIDE;
		}
		return type(mob).isBlockDangerous(s) ? Space.DANGEROUS : Space.OK;
	}

	@Override
	public boolean water(int x, int y, int z) {
		return level.getFluidState(at(x, y, z)).is(FluidTags.WATER);
	}

	@Override
	public boolean lava(int x, int y, int z) {
		return level.getFluidState(at(x, y, z)).is(FluidTags.LAVA);
	}

	@Override
	public boolean waterBlock(int x, int y, int z) {
		return level.getBlockState(at(x, y, z)).is(Blocks.WATER);
	}

	@Override
	public boolean air(int x, int y, int z) {
		return level.getBlockState(at(x, y, z)).isAir();
	}

	@Override
	public boolean conductor(int x, int y, int z) {
		BlockPos pos = at(x, y, z);
		return level.getBlockState(pos).isRedstoneConductor(level, pos);
	}

	@Override
	public boolean is(int x, int y, int z, String blockOrTag) {
		BlockState s = level.getBlockState(at(x, y, z));
		if (blockOrTag.equals(RuleEngine.INFESTABLE)) {
			return InfestedBlock.isCompatibleHostBlock(s);
		}
		if (blockOrTag.startsWith("#")) {
			return s.is(blockTags.computeIfAbsent(blockOrTag, t -> TagKey.create(Registries.BLOCK, Identifier.parse(t.substring(1)))));
		}
		return s.is(blocks.computeIfAbsent(blockOrTag, id -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))));
	}

	@Override
	public boolean spawnBoxFree(MobSpec mob, int x, int y, int z) {
		// blocks and the world border; entities are left out on purpose: they move all the time and the
		// overlay is only recomputed on block and light changes (the "why" panel says so)
		AABB box = type(mob).getSpawnAABB(x + 0.5, y, z + 0.5);
		return level.noBlockCollision(null, box) && level.noBorderCollision(null, box);
	}

	@Override
	public boolean liquidInBox(MobSpec mob, int x, int y, int z) {
		return level.containsAnyLiquid(type(mob).getDimensions().makeBoundingBox(x + 0.5, y, z + 0.5));
	}

	@Override
	public int skyLight(int x, int y, int z) {
		return level.getBrightness(LightLayer.SKY, at(x, y, z));
	}

	@Override
	public int blockLight(int x, int y, int z) {
		return level.getBrightness(LightLayer.BLOCK, at(x, y, z));
	}

	@Override
	public boolean canSeeSky(int x, int y, int z) {
		return level.canSeeSky(at(x, y, z));
	}

	@Override
	public boolean canSeeSkyFromBelowWater(int x, int y, int z) {
		return level.canSeeSkyFromBelowWater(at(x, y, z));
	}

	@Override
	public int worldSurface(int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
	}

	@Override
	public boolean insideBorder(int x, int y, int z) {
		return level.getWorldBorder().isWithinBounds(at(x, y, z));
	}

	@Override
	public boolean biomeIs(int x, int y, int z, String tag) {
		return level.getBiome(at(x, y, z)).is(biomeTags.computeIfAbsent(tag, t -> TagKey.create(Registries.BIOME, Identifier.parse(t.substring(1)))));
	}

	@Override
	public Boolean slimeChunk(int chunkX, int chunkZ) {
		return seed == null ? null : SlimeChunks.isSlimeChunk(seed, chunkX, chunkZ);
	}

	/** True when the block can hold some natural spawn at all: not a full collision block, and something below or a fluid here. */
	public static boolean candidate(Level level, BlockPos pos, BlockState s, BlockState below) {
		if (s.isCollisionShapeFullBlock(level, pos)) {
			return false;
		}
		FluidState f = s.getFluidState();
		return !below.isAir() || !f.isEmpty();
	}
}
