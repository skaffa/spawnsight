package dev.spawnsight.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import dev.spawnsight.core.config.ConfigStore;
import dev.spawnsight.core.config.SpawnsightConfig;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

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
		LOG.info("Spawnsight loaded; config in {}", STORE.dir());
	}

	private static void tick(Minecraft mc) {
		while (overlayKey.consumeClick()) {
			toggleOverlay(mc);
		}
		while (spheresKey.consumeClick()) {
			config.showSpheres = !config.showSpheres;
			save();
			message(mc, "Spheres " + (config.showSpheres ? "on" : "off"));
		}
		while (whyKey.consumeClick()) {
			message(mc, "The \"why\" panel is not built yet (milestone M3).");
		}
	}

	private static void toggleOverlay(Minecraft mc) {
		config.overlay = !config.overlay;
		save();
		message(mc, "Overlay " + (config.overlay ? "on" : "off") + " (the overlay itself arrives in milestone M3)");
	}

	private static void message(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("[Spawnsight] " + text));
		}
	}

	static List<String> help() {
		return List.of(
				"[Spawnsight] Shows where mobs can spawn and why.",
				"  " + overlayKey.getTranslatedKeyMessage().getString() + " or /spawnsight: overlay on/off",
				"  " + whyKey.getTranslatedKeyMessage().getString() + ": explain the block you look at",
				"  " + spheresKey.getTranslatedKeyMessage().getString() + ": spawn/despawn spheres (24, 32, 128)",
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
