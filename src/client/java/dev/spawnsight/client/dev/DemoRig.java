package dev.spawnsight.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;

/**
 * A test and demo rig in the sky (y 199-205), built with server commands in singleplayer: a closed dark
 * stone room (inside 13 × 4 × 13) with strips of floor variants, and an open grass platform east of it.
 * <pre>
 *   floor y 199: bottom slabs | top slabs | glass          (z +2..+3)
 *   on the floor: carpets     | rails     | stone buttons  (z +6..+7)
 *   1-high gap under a lowered ceiling (x +2..+5, z +10..+12); 2-high corridor, 1 wide, along z +11 (x +7..+13);
 *   redstone torch (light 7) at (+12, +4)
 * </pre>
 */
final class DemoRig {
	static final int Y = 199;

	private DemoRig() {
	}

	/** Origin (room corner) for a rig near the player. */
	static BlockPos origin(Minecraft mc) {
		BlockPos p = mc.player.blockPosition();
		return new BlockPos(p.getX() + 6, Y, p.getZ() + 6);
	}

	static void build(Minecraft mc, BlockPos o) {
		int x = o.getX();
		int z = o.getZ();
		cmd(mc, fill(x - 3, Y, z - 3, x + 32, Y, z + 17, "grass_block"));
		cmd(mc, fill(x - 3, Y + 1, z - 3, x + 32, Y + 8, z + 17, "air"));
		cmd(mc, fill(x, Y, z, x + 14, Y + 5, z + 14, "stone hollow"));
		cmd(mc, fill(x + 2, Y, z + 2, x + 4, Y, z + 3, "stone_slab[type=bottom]"));
		cmd(mc, fill(x + 6, Y, z + 2, x + 8, Y, z + 3, "smooth_stone_slab[type=top]"));
		cmd(mc, fill(x + 10, Y, z + 2, x + 12, Y, z + 3, "glass"));
		cmd(mc, fill(x + 2, Y + 1, z + 6, x + 4, Y + 1, z + 7, "white_carpet"));
		cmd(mc, fill(x + 6, Y + 1, z + 6, x + 8, Y + 1, z + 7, "rail"));
		cmd(mc, fill(x + 10, Y + 1, z + 6, x + 12, Y + 1, z + 7, "stone_button[face=floor]"));
		cmd(mc, fill(x + 2, Y + 2, z + 10, x + 5, Y + 4, z + 12, "stone"));
		// 2 high (no endermen) with a 1-wide corridor along z + 11 (no spiders)
		cmd(mc, fill(x + 7, Y + 3, z + 10, x + 13, Y + 4, z + 12, "stone"));
		cmd(mc, fill(x + 7, Y + 1, z + 10, x + 13, Y + 2, z + 10, "stone"));
		cmd(mc, fill(x + 7, Y + 1, z + 12, x + 13, Y + 2, z + 12, "stone"));
		cmd(mc, "setblock " + (x + 12) + " " + (Y + 1) + " " + (z + 4) + " redstone_torch");
		cmd(mc, "kill @e[type=!player]");
	}

	/** Spectator camera inside the room, in a corner, looking across the floor. */
	static String insideCamera(BlockPos o) {
		return String.format("tp SpawnTester %.1f %.1f %.1f -45 38", o.getX() + 1.3, Y + 3.2, o.getZ() + 1.3);
	}

	/** Camera east of the room above the grass, looking west at the room and the platform. */
	static String outsideCamera(BlockPos o) {
		return String.format("tp SpawnTester %.1f %.1f %.1f 70 28", o.getX() + 30.0, Y + 9.0, o.getZ() + 12.0);
	}

	private static String fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		return "fill " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1 + " " + block;
	}

	/** Inclusive bounds of everything the rig touches: {minX, minY, minZ, maxX, maxY, maxZ}. */
	static int[] bounds(BlockPos o) {
		return new int[] {o.getX() - 3, Y, o.getZ() - 3, o.getX() + 32, Y + 7, o.getZ() + 17};
	}

	static void cmd(Minecraft mc, String command) {
		IntegratedServer server = mc.getSingleplayerServer();
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
	}
}
