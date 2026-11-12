package dev.spawnsight.client;

import dev.spawnsight.client.probe.Mobs;
import dev.spawnsight.client.scan.Scanner;
import dev.spawnsight.client.source.SectionLists;
import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.rules.Env;
import dev.spawnsight.core.rules.SpotEvaluator;
import dev.spawnsight.core.rules.Verdict;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "why" explanation for the block the player looks at: the spawn position is the block above the
 * aimed face's block. M2 prints it to chat; M3 turns it into a panel (PLAN.md §6).
 */
public final class Why {
	private Why() {
	}

	/** The spawn position for the current crosshair target, or null. */
	public static BlockPos aimed(Minecraft mc) {
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return hit.getBlockPos().above();
		}
		return null;
	}

	/** Lines explaining the spot, or a single line saying why there is nothing to explain yet. */
	public static List<Component> explain(Minecraft mc, BlockPos pos) {
		List<Component> out = new ArrayList<>();
		Env env = Scanner.env();
		if (env == null || Scanner.world() == null) {
			out.add(Component.literal("Turn the overlay on first."));
			return out;
		}
		SectionLists lists = Scanner.listsFor(mc.level, pos);
		if (lists == null) {
			out.add(Component.literal("Reading spawn lists from the server... try again."));
			return out;
		}
		int idx = SectionLists.index(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		SpotEvaluator.Spot spot = SpotEvaluator.evaluate(Scanner.world(), env, (x, y, z, c) -> lists.at(idx, c), Mobs::spec, true,
				SpawnsightClient.config.mobFilter, pos.getX(), pos.getY(), pos.getZ());
		out.add(Component.literal(String.format(Locale.ROOT, "Spawns at %d %d %d (%s):", pos.getX(), pos.getY(), pos.getZ(),
				lists.origin() == SectionLists.Origin.SERVER ? "exact, from the server" : "vanilla tables; data packs and plugins may differ"))
				.withStyle(ChatFormatting.GRAY));
		if (spot.hostile().isEmpty() && lists.at(idx, Category.MONSTER).isEmpty()) {
			out.add(Component.literal("  no monsters in the spawn list here").withStyle(ChatFormatting.GREEN));
		}
		addAll(out, spot.hostile());
		addAll(out, spot.passive());
		return out;
	}

	private static void addAll(List<Component> out, List<Verdict> verdicts) {
		for (Verdict v : verdicts) {
			ChatFormatting color = switch (v.status()) {
				case NOW -> v.mob().category() == Category.MONSTER ? ChatFormatting.RED : ChatFormatting.GREEN;
				case LATER -> ChatFormatting.YELLOW;
				case UNKNOWN -> ChatFormatting.LIGHT_PURPLE;
				case NEVER -> ChatFormatting.DARK_GRAY;
			};
			out.add(Component.literal("  " + v.mob().shortName() + ": " + v.reason()).withStyle(color));
		}
	}
}
