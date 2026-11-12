package dev.spawnsight.client;

import dev.spawnsight.core.config.SpawnsightConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Spawnsight settings (Mod Menu → Spawnsight, or {@code /spawnsight config}). Plain vanilla widgets, so Spawnsight needs no
 * config library. Changes apply at once and are saved to {@code config/spawnsight/config.json} on close.
 */
public final class ConfigScreen extends Screen {
	private static final int W = 150;
	private static final int H = 20;
	private static final int GAP = 4;

	private final Screen parent;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Spawnsight settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		SpawnsightConfig c = SpawnsightClient.config;
		List<AbstractWidget> w = new ArrayList<>();

		w.add(toggle("Overlay", () -> c.overlay, v -> c.overlay = v,
				"Colour the blocks around you: red = a hostile mob can spawn now, yellow = only at night / in a thunderstorm / "
						+ "another moon phase, green = a passive mob can spawn (key U)."));
		w.add(intSlider("Radius", 4, 64, 2, () -> c.radius, v -> c.radius = v, v -> v + " blocks",
				"Blocks within this distance of you are checked and coloured. 32 shows a whole mob farm; larger costs more."));
		w.add(toggle("Yellow layer", () -> c.showSometimes, v -> c.showSometimes = v,
				"Also show blocks where a hostile mob can spawn only at night, in a thunderstorm or in another moon phase."));
		w.add(toggle("Green layer", () -> c.showPassive, v -> c.showPassive = v,
				"Also show blocks where a passive, ambient or water mob (cow, bat, squid...) can spawn."));
		w.add(toggle("Strict 24-block rule", () -> c.strictDistance, v -> c.strictDistance = v,
				"Mobs never spawn within 24 blocks of a player. Off: blocks near you show what spawns there when you stand "
						+ "elsewhere (hatched). On: they show nothing, as at this moment."));
		w.add(toggle("Spheres", () -> c.showSpheres, v -> c.showSpheres = v,
				"Outlines around you at 24 blocks (no spawning closer), 32 (random despawn starts) and 128 (instant despawn) (key Y)."));
		w.add(toggle("Aim hint", () -> c.aimHint, v -> c.aimHint = v,
				"While the overlay is on, a HUD line says what can spawn on the block you look at (key I for the full reason)."));
		w.add(intSlider("Opacity", 10, 100, 5, () -> c.opacity, v -> c.opacity = v, v -> v + " %",
				"How strongly the overlay colours the blocks."));
		w.add(intSlider("Scan budget", 1, 10, 1, () -> c.scanBudgetMs, v -> c.scanBudgetMs = v, v -> v + " ms/tick",
				"Time per game tick the block scanner may use. Lower it if the game stutters; the overlay then fills in more slowly."));

		int rows = (w.size() + 1) / 2;
		int top = Math.max(28, (height - rows * (H + GAP) - 30) / 2);
		int left = width / 2 - W - GAP / 2;
		for (int i = 0; i < w.size(); i++) {
			AbstractWidget widget = w.get(i);
			widget.setPosition(left + (i % 2) * (W + GAP), top + (i / 2) * (H + GAP));
			addRenderableWidget(widget);
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, top + rows * (H + GAP) + 8, 200, H).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		SpawnsightClient.config.clamp();
		SpawnsightClient.save();
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	// ---- widgets ------------------------------------------------------------------------------------

	private static CycleButton<Boolean> toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set, String tip) {
		CycleButton<Boolean> b = CycleButton.onOffBuilder(get.get()).create(0, 0, W, H, Component.literal(label), (btn, v) -> set.accept(v));
		b.setTooltip(Tooltip.create(Component.literal(tip)));
		return b;
	}

	private static AbstractWidget intSlider(String label, int min, int max, int step, Supplier<Integer> get, Consumer<Integer> set,
			java.util.function.IntFunction<String> fmt, String tip) {
		return slider(label, min, max, step, () -> (double) get.get(), v -> set.accept((int) Math.round(v)), v -> fmt.apply((int) Math.round(v)), tip);
	}

	/** A slider over {@code [min, max]} in steps of {@code step}; the label reads "Name: value". */
	private static AbstractWidget slider(String label, double min, double max, double step, Supplier<Double> get, Consumer<Double> set,
			DoubleFunction<String> fmt, String tip) {
		double start = (Math.max(min, Math.min(max, get.get())) - min) / (max - min);
		AbstractSliderButton s = new AbstractSliderButton(0, 0, W, H, Component.empty(), start) {
			{
				updateMessage();
			}

			private double current() {
				double v = min + value * (max - min);
				return Math.max(min, Math.min(max, Math.round(v / step) * step));
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label + ": " + fmt.apply(current())));
			}

			@Override
			protected void applyValue() {
				set.accept(current());
			}
		};
		s.setTooltip(Tooltip.create(Component.literal(tip)));
		return s;
	}
}
