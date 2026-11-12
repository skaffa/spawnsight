package dev.spawnsight.core.rules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A small block grid for rule tests. Blocks have a collision height (1 = full, 0.5 = bottom slab,
 * 1/16 = carpet, 0 = none), a sturdy top face, optional fluid and tags; light is set per block.
 */
final class FakeWorld implements World {
	static final class B {
		final String id;
		double collisionTop;
		boolean fullBlock;
		boolean sturdyTop;
		int emission;
		boolean signal;
		String fluid;
		boolean preventInside;
		boolean dangerous;
		boolean conductor;
		/** Floor rule override: null = default (sturdy top and emission < 14). */
		Boolean floorRule;
		final Set<String> tags = new HashSet<>();

		B(String id) {
			this.id = id;
		}

		B tag(String t) {
			tags.add(t);
			return this;
		}
	}

	static B solid(String id) {
		B b = new B(id);
		b.collisionTop = 1;
		b.fullBlock = true;
		b.sturdyTop = true;
		b.conductor = true;
		return b;
	}

	static final B STONE = solid("minecraft:stone");
	static final B GRASS = solid("minecraft:grass_block").tag("#minecraft:animals_spawnable_on").tag("#minecraft:goats_spawnable_on")
			.tag("#minecraft:wolves_spawnable_on");
	static final B SAND = solid("minecraft:sand").tag("#minecraft:sand").tag("#minecraft:camels_spawnable_on");
	static final B NETHERRACK = solid("minecraft:netherrack");
	static final B WART = solid("minecraft:nether_wart_block");
	static final B GLASS = glass();
	static final B GLOWSTONE = glowstone();
	static final B BOTTOM_SLAB = slab(false);
	static final B TOP_SLAB = slab(true);
	static final B CARPET = carpet();
	static final B RAIL = rail();
	static final B BUTTON = button();
	static final B WATER = water();

	private static B glass() {
		B b = solid("minecraft:glass");
		b.floorRule = false;
		b.conductor = false;
		return b;
	}

	private static B glowstone() {
		B b = solid("minecraft:glowstone");
		b.emission = 15;
		return b;
	}

	private static B slab(boolean top) {
		B b = new B(top ? "top slab" : "bottom slab");
		b.collisionTop = top ? 1 : 0.5; // a top slab is approximated as full height
		b.sturdyTop = top;
		return b;
	}

	private static B carpet() {
		B b = new B("minecraft:white_carpet");
		b.collisionTop = 1 / 16.0;
		return b;
	}

	private static B rail() {
		B b = new B("minecraft:rail");
		b.preventInside = true;
		return b;
	}

	private static B button() {
		B b = new B("minecraft:stone_button");
		b.signal = true;
		return b;
	}

	private static B water() {
		B b = new B("minecraft:water");
		b.fluid = "water";
		return b;
	}

	private final Map<Long, B> blocks = new HashMap<>();
	private final Map<Long, int[]> light = new HashMap<>();
	final Set<String> biomeTags = new HashSet<>();
	Boolean slimeChunk = false;
	int defaultSky = 0;
	int defaultBlock = 0;

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	FakeWorld set(int x, int y, int z, B b) {
		blocks.put(key(x, y, z), b);
		return this;
	}

	/** Fills a box (inclusive) with a block. */
	FakeWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, B b) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					set(x, y, z, b);
				}
			}
		}
		return this;
	}

	FakeWorld light(int x, int y, int z, int sky, int block) {
		light.put(key(x, y, z), new int[] {sky, block});
		return this;
	}

	B at(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	@Override
	public String name(int x, int y, int z) {
		B b = at(x, y, z);
		return b == null ? "air" : b.id;
	}

	@Override
	public Floor floor(int x, int y, int z, MobSpec mob) {
		B b = at(x, y, z);
		if (b == null) {
			return Floor.NOT_STURDY;
		}
		boolean valid = b.floorRule != null ? b.floorRule : b.sturdyTop && b.emission < 14;
		if (valid) {
			return Floor.OK;
		}
		if (!b.sturdyTop) {
			return Floor.NOT_STURDY;
		}
		return b.emission >= 14 && b.floorRule == null ? Floor.EMITS_LIGHT : Floor.BLOCK_RULE;
	}

	@Override
	public Space space(int x, int y, int z, MobSpec mob) {
		B b = at(x, y, z);
		if (b == null) {
			return Space.OK;
		}
		if (b.fullBlock) {
			return Space.FULL_BLOCK;
		}
		if (b.signal) {
			return Space.SIGNAL_SOURCE;
		}
		if (b.fluid != null) {
			return Space.FLUID;
		}
		if (b.preventInside) {
			return Space.NO_SPAWN_INSIDE;
		}
		return b.dangerous && !mob.fireImmune() ? Space.DANGEROUS : Space.OK;
	}

	@Override
	public boolean water(int x, int y, int z) {
		B b = at(x, y, z);
		return b != null && "water".equals(b.fluid);
	}

	@Override
	public boolean lava(int x, int y, int z) {
		B b = at(x, y, z);
		return b != null && "lava".equals(b.fluid);
	}

	@Override
	public boolean waterBlock(int x, int y, int z) {
		return at(x, y, z) == WATER;
	}

	@Override
	public boolean air(int x, int y, int z) {
		return at(x, y, z) == null;
	}

	@Override
	public boolean conductor(int x, int y, int z) {
		B b = at(x, y, z);
		return b != null && b.conductor;
	}

	@Override
	public boolean is(int x, int y, int z, String blockOrTag) {
		B b = at(x, y, z);
		if (b == null) {
			return false;
		}
		return blockOrTag.startsWith("#") ? b.tags.contains(blockOrTag) : b.id.equals(blockOrTag);
	}

	@Override
	public boolean spawnBoxFree(MobSpec mob, int x, int y, int z) {
		double hw = mob.spawnWidth() / 2;
		return !collides(x + 0.5 - hw, y, z + 0.5 - hw, x + 0.5 + hw, y + mob.spawnHeight(), z + 0.5 + hw);
	}

	private boolean collides(double x0, double y0, double z0, double x1, double y1, double z1) {
		double eps = 1e-7;
		for (int bx = (int) Math.floor(x0); bx <= (int) Math.floor(x1); bx++) {
			for (int by = (int) Math.floor(y0) - 1; by <= (int) Math.floor(y1); by++) {
				for (int bz = (int) Math.floor(z0); bz <= (int) Math.floor(z1); bz++) {
					B b = at(bx, by, bz);
					if (b == null || b.collisionTop <= 0) {
						continue;
					}
					if (x0 < bx + 1 - eps && x1 > bx + eps && y0 < by + b.collisionTop - eps && y1 > by + eps && z0 < bz + 1 - eps && z1 > bz + eps) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean liquidInBox(MobSpec mob, int x, int y, int z) {
		double hw = mob.boxWidth() / 2;
		int x0 = (int) Math.floor(x + 0.5 - hw);
		int x1 = (int) Math.ceil(x + 0.5 + hw);
		int y1 = (int) Math.ceil(y + mob.boxHeight());
		int z0 = (int) Math.floor(z + 0.5 - hw);
		int z1 = (int) Math.ceil(z + 0.5 + hw);
		for (int bx = x0; bx < x1; bx++) {
			for (int by = y; by < y1; by++) {
				for (int bz = z0; bz < z1; bz++) {
					B b = at(bx, by, bz);
					if (b != null && b.fluid != null) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public int skyLight(int x, int y, int z) {
		int[] l = light.get(key(x, y, z));
		return l == null ? defaultSky : l[0];
	}

	@Override
	public int blockLight(int x, int y, int z) {
		int[] l = light.get(key(x, y, z));
		return l == null ? defaultBlock : l[1];
	}

	@Override
	public boolean canSeeSky(int x, int y, int z) {
		for (int yy = y + 1; yy < y + 64; yy++) {
			if (at(x, yy, z) != null) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean canSeeSkyFromBelowWater(int x, int y, int z) {
		for (int yy = y + 1; yy < y + 64; yy++) {
			B b = at(x, yy, z);
			if (b != null && b.fluid == null) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int worldSurface(int x, int z) {
		for (int y = 200; y >= -64; y--) {
			if (at(x, y, z) != null) {
				return y + 1;
			}
		}
		return -64;
	}

	@Override
	public boolean insideBorder(int x, int y, int z) {
		return true;
	}

	@Override
	public boolean biomeIs(int x, int y, int z, String tag) {
		return biomeTags.contains(tag);
	}

	@Override
	public Boolean slimeChunk(int chunkX, int chunkZ) {
		return slimeChunk;
	}
}
