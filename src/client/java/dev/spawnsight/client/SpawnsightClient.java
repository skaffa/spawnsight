package dev.spawnsight.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import dev.spawnsight.client.dev.SelfTest;
import dev.spawnsight.client.render.OverlayRenderer;
import dev.spawnsight.client.render.SpawnHud;
import dev.spawnsight.client.render.SphereRenderer;
import dev.spawnsight.client.scan.Scanner;
import dev.spawnsight.core.config.ConfigStore;
import dev.spawnsight.core.config.SpawnsightConfig;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Client entrypoint: config, keys, commands. The scanner and overlay arrive in M2/M3 (PLAN.md §11). */
public final class SpawnsightClient implements ClientModInitializer {
	public static final String MOD_ID = "spawnsight";
	public static final Logger LOG = LoggerFactory.getLogger("Spawnsight");

	/** All Spawnsight files live in their own folder: {@code config/spawnsight/}. */
	public static final ConfigStore STORE = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
	public static SpawnsightConfig config = new SpawnsightConfig();

	private static KeyMapping overlayKey;
	private static KeyMapping whyKey;
	private static KeyMapping spheresKey;

	@Override
	public void onInitializeClient() {
		try {
			config = STORE.load();
			if (STORE.lastBackup() != null) {
				LOG.warn("Broken {} moved to {}; using defaults", STORE.file(), STORE.lastBackup());
			}
		} catch (IOException e) {
			LOG.warn("Could not load {}; using defaults", STORE.file(), e);
		}

		// U, I and Y have no vanilla 26.3 default; H is used by Vire and Ortho, J N M B K V by Vire.
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		overlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.spawnsight.overlay", InputConstants.KEY_U, category));
		whyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.spawnsight.why", InputConstants.KEY_I, category));
		spheresKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.spawnsight.spheres", InputConstants.KEY_Y, category));

		ClientTickEvents.END_CLIENT_TICK.register(SpawnsightClient::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(OverlayRenderer::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), SpawnHud::render);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				ClientCommands.literal(MOD_ID)
						.executes(ctx -> {
							toggleOverlay(Minecraft.getInstance());
							return 1;
						})
						.then(ClientCommands.literal("radius")
								.then(ClientCommands.argument("blocks", IntegerArgumentType.integer(4, 64)).executes(ctx -> {
									config.radius = IntegerArgumentType.getInteger(ctx, "blocks");
									save();
									ctx.getSource().sendFeedback(Component.literal("[Spawnsight] Radius " + config.radius + " blocks"));
									return 1;
								})))
						.then(ClientCommands.literal("why").executes(ctx -> {
							Minecraft mc = Minecraft.getInstance();
							BlockPos pos = Why.aimed(mc);
							if (pos == null) {
								ctx.getSource().sendError(Component.literal("[Spawnsight] Look at a block first"));
								return 0;
							}
							Why.explain(mc, Why.evaluate(mc, pos)).forEach(ctx.getSource()::sendFeedback);
							return 1;
						}))
						.then(ClientCommands.literal("mob")
								.then(ClientCommands.literal("all").executes(ctx -> {
									config.mobFilter = null;
									save();
									ctx.getSource().sendFeedback(Component.literal("[Spawnsight] Showing every mob"));
									return 1;
								}))
								.then(ClientCommands.argument("entity", IdentifierArgument.id())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), b))
										.executes(ctx -> {
											Identifier id = ctx.getArgument("entity", Identifier.class);
											if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
												ctx.getSource().sendError(Component.literal("[Spawnsight] Unknown entity " + id));
												return 0;
											}
											config.mobFilter = id.toString();
											save();
											ctx.getSource().sendFeedback(Component.literal("[Spawnsight] Farm mode: only where " + id.getPath()
													+ " can spawn (/spawnsight mob all to undo)"));
											return 1;
										})))
						.then(ClientCommands.literal("stats").executes(ctx -> {
							for (String line : stats()) {
								ctx.getSource().sendFeedback(Component.literal(line));
							}
							return 1;
						}))
						.then(ClientCommands.literal("config").executes(ctx -> {
							Minecraft mc = Minecraft.getInstance();
							mc.execute(() -> mc.gui.setScreen(new ConfigScreen(null))); // after the chat screen closed
							return 1;
						}))
						.then(ClientCommands.literal("help").executes(ctx -> {
							for (String line : help()) {
								ctx.getSource().sendFeedback(Component.literal(line));
							}
							return 1;
						}))));
		SelfTest.init();
		LOG.info("Spawnsight loaded; config in {}", STORE.dir());
	}

	private static void tick(Minecraft mc) {
		SelfTest.tick(mc);
		Scanner.tick(mc);
		SphereRenderer.tick(mc);
		SpawnHud.tick(mc);
		while (overlayKey.consumeClick()) {
			toggleOverlay(mc);
		}
		while (spheresKey.consumeClick()) {
			config.showSpheres = !config.showSpheres;
			save();
			message(mc, "Spheres " + (config.showSpheres ? "on" : "off"));
		}
		while (whyKey.consumeClick()) {
			BlockPos pos = Why.aimed(mc);
			SpawnHud.panel = !SpawnHud.panel;
			if (SpawnHud.panel && !config.overlay) {
				message(mc, "Turn the overlay on (key U) to see why");
			}
		}
	}

	private static void toggleOverlay(Minecraft mc) {
		config.overlay = !config.overlay;
		save();
		message(mc, "Overlay " + (config.overlay ? "on" : "off") + " (drawing arrives in milestone M3; /spawnsight why and stats work)");
	}

	private static void message(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("[Spawnsight] " + text));
		}
	}

	static List<String> stats() {
		Scanner.Stats st = Scanner.STATS;
		int[] shades = Scanner.shadeCounts();
		double avg = st.sectionsComputed == 0 ? 0 : st.nanosComputing / 1e6 / st.sectionsComputed;
		return List.of(
				String.format(Locale.ROOT, "[Spawnsight] %d sections cached, queue %d (%d waiting for the server), source %s",
						Scanner.cachedSections(), st.queue, st.waitingForServer, Scanner.origin()),
				String.format(Locale.ROOT, "  computed %d sections, avg %.2f ms, max %.2f ms; tick max %.2f ms, last %.2f ms",
						st.sectionsComputed, avg, st.maxSectionNanos / 1e6, st.maxTickNanos / 1e6, st.lastTickNanos / 1e6),
				String.format(Locale.ROOT, "  last full fill %s; blocks: %d red, %d yellow, %d unknown, %d green",
						st.lastFillNanos < 0 ? "running" : st.lastFillNanos / 1_000_000 + " ms", shades[4], shades[3], shades[2], shades[1]));
	}

	static List<String> help() {
		return List.of(
				"[Spawnsight] Shows where mobs can spawn and why.",
				"  " + overlayKey.getTranslatedKeyMessage().getString() + " or /spawnsight: overlay on/off",
				"  " + whyKey.getTranslatedKeyMessage().getString() + ": panel explaining the block you look at",
				"  " + spheresKey.getTranslatedKeyMessage().getString() + ": spawn/despawn spheres (24, 32, 128)",
				"  /spawnsight why: explain the block you look at, in chat",
				"  /spawnsight mob <entity>|all: farm mode, show only one mob",
				"  /spawnsight stats: scanner numbers",
				"  /spawnsight radius <4-64>: scan radius",
				"  /spawnsight config: settings",
				"  /spawnsight help: this list");
	}

	public static void save() {
		try {
			STORE.save(config);
		} catch (IOException e) {
			LOG.warn("Could not save {}", STORE.file(), e);
		}
	}
}
