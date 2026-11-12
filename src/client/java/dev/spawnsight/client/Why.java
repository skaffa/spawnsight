package dev.spawnsight.client;

import dev.spawnsight.client.probe.Mobs;
import dev.spawnsight.client.scan.Scanner;
import dev.spawnsight.client.source.SectionLists;
import dev.spawnsight.core.rules.Category;
import dev.spawnsight.core.rules.Env;
import dev.spawnsight.core.rules.SpotEvaluator;
import dev.spawnsight.core.rules.Verdict;
import dev.spawnsight.core.spheres.Spheres;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "why" explanation for the block the player looks at (PLAN.md §6). The spawn position is the block
 * above the aimed block. Used by the HUD line, the panel (key I) and {@code /spawnsight why}.
 */
public final class Why {
	/** A full evaluation of one spawn position, or a message when there is none yet. */
	public record Result(BlockPos pos, SpotEvaluator.Spot spot, SectionLists.Origin origin, boolean noMonsterList, String message) {
	}

	private Why() {
	}

	/** The spawn position for the current crosshair target, or null. */
	public static BlockPos aimed(Minecraft mc) {
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return hit.getBlockPos().above();
		}
		return null;
	}

	public static Result evaluate(Minecraft mc, BlockPos pos) {
		Env env = Scanner.env();
		if (env == null || Scanner.world() == null) {
			return new Result(pos, null, null, false, "Turn the overlay on first (key U).");
		}
		SectionLists lists = Scanner.listsFor(mc.level, pos);
		if (lists == null) {
			return new Result(pos, null, null, false, "Reading spawn lists from the server...");
		}
		int idx = SectionLists.index(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		SpotEvaluator.Spot spot = SpotEvaluator.evaluate(Scanner.world(), env, (x, y, z, c) -> lists.at(idx, c), Mobs::spec, true,
				SpawnsightClient.config.mobFilter, pos.getX(), pos.getY(), pos.getZ());
		return new Result(pos, spot, lists.origin(), lists.at(idx, Category.MONSTER).isEmpty(), null);
	}

	/** One line for the HUD: who can spawn here, or the first reason nothing can. */
	public static Component summary(Minecraft mc, Result r) {
		if (r.spot == null) {
			return Component.literal(r.message).withStyle(ChatFormatting.GRAY);
		}
		List<String> now = new ArrayList<>();
		List<String> later = new ArrayList<>();
		List<String> passive = new ArrayList<>();
		String unknown = null;
		for (Verdict v : r.spot.hostile()) {
			switch (v.status()) {
				case NOW -> now.add(v.mob().shortName());
				case LATER -> later.add(v.mob().shortName());
				case UNKNOWN -> unknown = v.mob().shortName();
				case NEVER -> {
				}
			}
		}
		for (Verdict v : r.spot.passive()) {
			if (v.possibleSometime()) {
				passive.add(v.mob().shortName());
			}
		}
		MutableComponent out = Component.empty();
		if (!now.isEmpty()) {
			out.append(Component.literal(names(now) + " can spawn now").withStyle(ChatFormatting.RED));
		} else if (!later.isEmpty()) {
			out.append(Component.literal(names(later) + " can spawn " + bestWhen(r.spot)).withStyle(ChatFormatting.YELLOW));
		} else if (unknown != null) {
			out.append(Component.literal(unknown + ": only in a slime chunk (seed unknown)").withStyle(ChatFormatting.LIGHT_PURPLE));
		} else if (r.noMonsterList) {
			out.append(Component.literal("no monsters spawn in this biome").withStyle(ChatFormatting.GREEN));
		} else {
			out.append(Component.literal("no hostile spawn: " + firstReason(r.spot)).withStyle(ChatFormatting.GRAY));
		}
		if (!passive.isEmpty() && now.isEmpty() && later.isEmpty()) {
			out.append(Component.literal("  (" + names(passive) + " can)").withStyle(ChatFormatting.GREEN));
		}
		if (mc.player != null && !Spheres.farEnough(Spheres.distSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ(), r.pos.getX(), r.pos.getY(),
				r.pos.getZ())) && (!now.isEmpty() || !later.isEmpty())) {
			out.append(Component.literal("  [not while you are within 24 blocks]").withStyle(ChatFormatting.DARK_GRAY));
		}
		return out;
	}

	/** All lines for the panel and chat. */
	public static List<Component> explain(Minecraft mc, Result r) {
		List<Component> out = new ArrayList<>();
		if (r.spot == null) {
			out.add(Component.literal(r.message));
			return out;
		}
		out.add(Component.literal(String.format(Locale.ROOT, "Spawns at %d %d %d (%s):", r.pos.getX(), r.pos.getY(), r.pos.getZ(),
				r.origin == SectionLists.Origin.SERVER ? "exact, from the integrated server" : "vanilla tables; data packs and plugins may differ"))
				.withStyle(ChatFormatting.GRAY));
		if (r.origin == SectionLists.Origin.TABLE) {
			out.add(Component.literal("  structure spawns (witch huts, monuments, outposts, fortresses) are not known here").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (r.noMonsterList) {
			out.add(Component.literal("  no monsters in the spawn list here").withStyle(ChatFormatting.GREEN));
		}
		addAll(out, r.spot.hostile());
		addAll(out, r.spot.passive());
		if (mc.player != null && !Spheres.farEnough(Spheres.distSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ(), r.pos.getX(), r.pos.getY(),
				r.pos.getZ()))) {
			out.add(Component.literal("  within 24 blocks of you: nothing spawns here while you stand this close").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (Scanner.server().available() && !Scanner.server().mobSpawning()) {
			out.add(Component.literal("  game rule doMobSpawning is off").withStyle(ChatFormatting.DARK_GRAY));
		}
		return out;
	}

	private static String bestWhen(SpotEvaluator.Spot spot) {
		for (Verdict v : spot.hostile()) {
			if (v.status() == Verdict.Status.LATER) {
				return v.bestWhen();
			}
		}
		return "later";
	}

	private static String firstReason(SpotEvaluator.Spot spot) {
		// the reason shared by most mobs is the useful one ("block light 3 > 0")
		java.util.Map<String, Integer> count = new java.util.LinkedHashMap<>();
		for (Verdict v : spot.hostile()) {
			if (v.status() == Verdict.Status.NEVER && v.reason() != null) {
				count.merge(v.reason(), 1, Integer::sum);
			}
		}
		return count.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse("nothing in the list");
	}

	private static String names(List<String> l) {
		if (l.size() <= 3) {
			return String.join(", ", l);
		}
		return String.join(", ", l.subList(0, 3)) + " +" + (l.size() - 3);
	}

	/** One line per (status, reason), listing every mob that shares it: "spider, zombie +3: block light 2 > 0". */
	private static void addAll(List<Component> out, List<Verdict> verdicts) {
		java.util.Map<String, List<Verdict>> groups = new java.util.LinkedHashMap<>();
		for (Verdict v : verdicts) {
			String key = v.status() + "|" + (v.mob().category() == Category.MONSTER) + "|" + v.reason();
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
		}
		for (List<Verdict> g : groups.values()) {
			Verdict v = g.get(0);
			ChatFormatting color = switch (v.status()) {
				case NOW -> v.mob().category() == Category.MONSTER ? ChatFormatting.RED : ChatFormatting.GREEN;
				case LATER -> v.mob().category() == Category.MONSTER ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
				case UNKNOWN -> ChatFormatting.LIGHT_PURPLE;
				case NEVER -> ChatFormatting.DARK_GRAY;
			};
			List<String> names = new ArrayList<>();
			for (Verdict x : g) {
				names.add(x.mob().shortName());
			}
			out.add(Component.literal("  " + String.join(", ", names) + ": " + v.reason()).withStyle(color));
		}
	}
}
