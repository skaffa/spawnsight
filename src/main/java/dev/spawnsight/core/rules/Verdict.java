package dev.spawnsight.core.rules;

import java.util.List;

/**
 * Whether one mob can spawn on one block.
 *
 * @param pNow     chance that the per-attempt checks pass now (0 when impossible now)
 * @param pBest    the highest chance over all situations in {@link Env#all()}
 * @param bestWhen name of the situation with {@code pBest} ("now", "at night"...), or null
 * @param reason   for {@link Status#NEVER}/{@link Status#UNKNOWN}: the rule that blocks it; otherwise a short summary
 * @param notes    extra facts for the "why" panel (checks Spawnsight cannot see, such as entities in the way)
 */
public record Verdict(MobSpec mob, Status status, double pNow, double pBest, String bestWhen, String reason, List<String> notes) {
	public enum Status {
		/** Can spawn in the current situation. */
		NOW,
		/** Not now, but in another situation (night, thunderstorm, moon phase). */
		LATER,
		/** Depends on something unknown (slime chunk without the seed). */
		UNKNOWN,
		/** Never, whatever the time and weather. */
		NEVER
	}

	public boolean possibleSometime() {
		return status == Status.NOW || status == Status.LATER;
	}
}
