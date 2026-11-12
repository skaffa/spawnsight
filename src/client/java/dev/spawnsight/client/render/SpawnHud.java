package dev.spawnsight.client.render;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.client.Why;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * HUD: a one-line hint under the crosshair for the aimed block while the overlay is on, and the full
 * "why" panel on the right (key I toggles it). The block is evaluated at most every 5 ticks or when the
 * aimed block changes.
 */
public final class SpawnHud {
	public static boolean panel;
	private static BlockPos lastPos;
	private static int age;
	private static Component line;
	private static List<Component> lines = List.of();

	private SpawnHud() {
	}

	public static void tick(Minecraft mc) {
		boolean wanted = SpawnsightClient.config.overlay && (SpawnsightClient.config.aimHint || panel);
		BlockPos pos = wanted && mc.level != null ? Why.aimed(mc) : null;
		if (pos == null) {
			line = null;
			lines = List.of();
			lastPos = null;
			return;
		}
		if (pos.equals(lastPos) && ++age < 5) {
			return;
		}
		age = 0;
		lastPos = pos;
		Why.Result r = Why.evaluate(mc, pos);
		line = Why.summary(mc, r);
		lines = panel ? Why.explain(mc, r) : List.of();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || mc.player == null) {
			return;
		}
		Font font = mc.font;
		if (line != null && SpawnsightClient.config.aimHint) {
			int w = font.width(line);
			int x = (g.guiWidth() - w) / 2;
			int y = g.guiHeight() / 2 + 24;
			g.fill(x - 3, y - 2, x + w + 3, y + 10, 0x90000000);
			g.text(font, line, x, y, 0xFFFFFFFF);
		}
		if (panel && !lines.isEmpty()) {
			// top left, wrapped; the right side is where vanilla toasts appear
			int maxW = Math.min(g.guiWidth() / 2 - 20, 340);
			java.util.List<net.minecraft.util.FormattedCharSequence> rows = new java.util.ArrayList<>();
			for (Component c : lines) {
				rows.addAll(font.split(c, maxW));
			}
			int widest = 0;
			for (var r : rows) {
				widest = Math.max(widest, font.width(r));
			}
			int maxRows = Math.max(4, (g.guiHeight() / 2 - 20) / 10);
			int n = Math.min(rows.size(), maxRows);
			int x = 8;
			int y = 8;
			g.fill(x - 4, y - 4, x + widest + 4, y + n * 10 + 2, 0xB0000000);
			for (int i = 0; i < n; i++) {
				g.text(font, rows.get(i), x, y + i * 10, 0xFFFFFFFF);
			}
		}
	}
}
