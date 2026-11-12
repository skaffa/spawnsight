package dev.spawnsight.client.dev;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.client.render.OverlayRenderer;
import dev.spawnsight.client.render.SpawnHud;
import dev.spawnsight.client.scan.Scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * M3 screenshots ({@code -Pselftest=shots}, singleplayer world "SpawnTest", run with
 * {@code --width 1600 --height 900}): overlay at night, by day, the "why" panel and the spheres.
 * Files go to {@code run/screenshots/}.
 */
final class ShotsTest {
	private ShotsTest() {
	}

	static void steps(SelfTest.Script s) {
		s.waitFor("singleplayer world loaded", () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.level != null && mc.player != null && mc.hasSingleplayerServer() && mc.player.tickCount > 60;
		}, 3600, mc -> {
			server(mc, "time set midnight");
			server(mc, "weather clear");
			server(mc, "difficulty normal");
			server(mc, "gamemode creative SpawnTester");
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
			SpawnsightClient.config.overlay = true;
			SpawnsightClient.config.radius = 32;
			SpawnsightClient.config.showPassive = true;
			SpawnsightClient.config.showSometimes = true;
			SpawnsightClient.config.showSpheres = false;
			SpawnsightClient.config.mobFilter = null;
			SpawnHud.panel = false;
		});
		s.step("build the rig", 20, mc -> {
			origin = DemoRig.origin(mc);
			DemoRig.build(mc, origin);
		});
		s.step("camera in the dark room", 40, mc -> server(mc, DemoRig.insideCamera(origin)));
		s.step("settle", 60, mc -> {
		});
		s.waitFor("night overlay filled", ShotsTest::filled, 6000, mc -> {
		});
		s.step("night shot", 40, mc -> {
			SpawnsightClient.LOG.info("[selftest] quads drawn: {}", OverlayRenderer.lastQuads);
			SelfTest.check(OverlayRenderer.lastQuads > 0, "overlay draws quads (" + OverlayRenderer.lastQuads + ")");
			shot(mc, "spawnsight-room.png");
			OverlayRenderer.drawNanos = 0;
			OverlayRenderer.drawFrames = 0;
			SpawnHud.panel = true;
		});
		s.step("why shot", 40, mc -> {
			shot(mc, "spawnsight-why.png");
			SpawnsightClient.LOG.info(String.format(java.util.Locale.ROOT, "[selftest] overlay draw: %.3f ms per frame over %d frames, %d quads",
					OverlayRenderer.drawNanos / 1e6 / Math.max(1, OverlayRenderer.drawFrames), OverlayRenderer.drawFrames, OverlayRenderer.lastQuads));
		});
		s.step("go outside, noon", 5, mc -> {
			SpawnHud.panel = false;
			server(mc, DemoRig.outsideCamera(origin));
			server(mc, "time set noon");
		});
		s.step("noon arrives", 60, mc -> {
		});
		s.waitFor("day overlay filled", ShotsTest::filled, 6000, mc -> {
		});
		s.step("day shot", 40, mc -> shot(mc, "spawnsight-day.png"));
		s.step("spheres on", 5, mc -> SpawnsightClient.config.showSpheres = true);
		s.step("spheres shot", 40, mc -> shot(mc, "spawnsight-spheres.png"));
		s.step("farm mode: endermen only, inside, night", 5, mc -> {
			SpawnsightClient.config.showSpheres = false;
			SpawnsightClient.config.mobFilter = "minecraft:enderman";
			server(mc, "time set midnight");
			server(mc, DemoRig.insideCamera(origin));
		});
		s.step("settle 2", 60, mc -> {
		});
		s.waitFor("filter filled", ShotsTest::filled, 6000, mc -> {
		});
		s.step("farm shot", 40, mc -> {
			shot(mc, "spawnsight-enderman.png");
			SpawnsightClient.config.mobFilter = null;
		});
	}

	private static net.minecraft.core.BlockPos origin;

	private static boolean filled() {
		return Scanner.STATS.lastFillNanos >= 0 && Scanner.STATS.queue == 0;
	}

	private static void shot(Minecraft mc, String file) {
		Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1,
				msg -> SpawnsightClient.LOG.info("[selftest] screenshot: {}", msg.getString()));
	}

	private static void server(Minecraft mc, String command) {
		DemoRig.cmd(mc, command);
		if (command.startsWith("tp ")) {
			mc.player.getAbilities().flying = true; // creative: hover where the camera was put
		}
	}
}
