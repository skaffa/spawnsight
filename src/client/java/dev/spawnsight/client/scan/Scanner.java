package dev.spawnsight.client.scan;

import dev.spawnsight.client.SpawnsightClient;
import dev.spawnsight.client.probe.Envs;
import dev.spawnsight.client.probe.LevelWorld;
import dev.spawnsight.client.probe.Mobs;
import dev.spawnsight.client.source.SectionLists;
import dev.spawnsight.client.source.ServerLists;
import dev.spawnsight.client.source.TableLists;
import dev.spawnsight.core.config.SpawnsightConfig;
import dev.spawnsight.core.rules.Env;
import dev.spawnsight.core.rules.SpotEvaluator;
import dev.spawnsight.core.table.SpawnTable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the overlay result for every 16³ section within the radius. A section is
 * computed once and again only when it is marked dirty (block or light change, via the renderer's own
 * dirty marks), when the {@link Env} changes (time, weather, moon, difficulty) or when settings change.
 * Work is spread over ticks with a time budget, nearest sections first. Client thread only.
 */
public final class Scanner {
	/** Result of one section: the shade of each block ({@link SpotEvaluator.Shade} ordinal), null when all NONE. */
	public static final class Section {
		byte[] shade;
		boolean dirty;
		/** Increased on every dirty mark, so a job that started before the mark does not clear it. */
		int dirtyStamp;
		/** Scanner tick of the last block/light dirty mark: wait a few ticks for more (chunk and light loading). */
		long dirtyAt = Long.MIN_VALUE / 2;
		int candidates;
		int[] counts = new int[SpotEvaluator.Shade.values().length];
		/** Increased on every recompute; the renderer rebuilds its mesh when this changes. */
		public int version;

		public int shadeAt(int index) {
			return shade == null ? 0 : shade[index];
		}

		public boolean empty() {
			return shade == null;
		}
	}

	private static final Long2ObjectOpenHashMap<Section> CACHE = new Long2ObjectOpenHashMap<>();
	private static final LongOpenHashSet DIRTY_EVENTS = new LongOpenHashSet();
	private static final ServerLists SERVER = new ServerLists();
	private static TableLists table;
	private static ClientLevel lastLevel;
	private static Env lastEnv;
	private static String lastSettings;
	private static LevelWorld world;
	private static Long worldSeed;
	/** Ticks since start, for the dirty debounce. */
	private static long ticks;
	/** Wait this many ticks after a block/light change before recomputing (more changes usually follow). */
	private static final int DEBOUNCE_TICKS = 4;
	private static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();

	public static final Stats STATS = new Stats();

	private Scanner() {
	}

	/** Timing numbers for {@code /spawnsight stats} and the self-test. */
	public static final class Stats {
		public long sectionsComputed;
		public long nanosComputing;
		public long maxSectionNanos;
		public long maxTickNanos;
		public long lastTickNanos;
		public int queue;
		public int waitingForServer;
		/** Nanos from the first tick of a full fill (after clear/env change) to an empty queue, or -1 while filling. */
		public long lastFillNanos = -1;
		long fillStart = -1;
		public long candidates;
		/** CPU time of the scanner thread (render thread) in scanner ticks, not wall time. */
		public long cpuNanos;
		public long maxTickCpuNanos;
		/** Scanner ticks that did any work (for the average CPU per working tick). */
		public long workTicks;

		public void reset() {
			sectionsComputed = 0;
			nanosComputing = 0;
			maxSectionNanos = 0;
			maxTickNanos = 0;
			candidates = 0;
			cpuNanos = 0;
			maxTickCpuNanos = 0;
			workTicks = 0;
		}
	}

	public static SpawnTable table() {
		if (table == null) {
			try (Reader r = new InputStreamReader(Objects.requireNonNull(Scanner.class.getResourceAsStream(SpawnTable.RESOURCE)),
					StandardCharsets.UTF_8)) {
				table = new TableLists(SpawnTable.read(r));
			} catch (java.io.IOException e) {
				throw new IllegalStateException("spawn table missing", e);
			}
		}
		return table.table();
	}

	public static ServerLists server() {
		return SERVER;
	}

	/** Called from the renderer's dirty marks (mixin), any thread: remembered and applied on the next tick. */
	public static void markDirty(int sx, int sy, int sz) {
		synchronized (DIRTY_EVENTS) {
			DIRTY_EVENTS.add(SectionPos.asLong(sx, sy, sz));
			// a mob below can reach into this section with its hitbox (up to 3 blocks tall)
			DIRTY_EVENTS.add(SectionPos.asLong(sx, sy - 1, sz));
		}
	}

	public static void clear() {
		CACHE.clear();
		synchronized (DIRTY_EVENTS) {
			DIRTY_EVENTS.clear();
		}
		SERVER.clear();
		if (table != null) {
			table.clear();
		}
		lastEnv = null;
		lastSettings = null;
		world = null;
		job = null;
		STATS.fillStart = -1;
	}

	public static Section section(long key) {
		return CACHE.get(key);
	}

	/** Current env (null before the first scan). */
	public static Env env() {
		return lastEnv;
	}

	/** The world view used for scanning (client level), or null. */
	public static LevelWorld world() {
		return world;
	}

	public static SectionLists.Origin origin() {
		return ServerLists.available() ? SectionLists.Origin.SERVER : SectionLists.Origin.TABLE;
	}

	/** Lists for one block, for the "why" panel; null while the server has not answered. */
	public static SectionLists listsFor(Level level, BlockPos pos) {
		long key = SectionPos.asLong(pos);
		BitSet one = new BitSet(4096);
		one.set(SectionLists.index(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15));
		if (ServerLists.available()) {
			return SERVER.get(key, one);
		}
		table();
		return table.get(level, key, one);
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			if (lastLevel != null) {
				clear();
				lastLevel = null;
			}
			return;
		}
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		SpawnsightConfig c = SpawnsightClient.config;
		SERVER.tick(mc);
		if (!c.overlay) {
			return;
		}
		ticks++;
		long t0 = System.nanoTime();
		long cpu0 = CPU.getCurrentThreadCpuTime();
		table();
		boolean sp = ServerLists.available();
		Long seed = sp ? SERVER.seed() : SpawnsightClient.serverSeed(mc);
		if (world == null || !java.util.Objects.equals(seed, worldSeed)) {
			world = new LevelWorld(level, seed);
			worldSeed = seed;
		}
		Env env = Envs.of(level, table(), sp ? SERVER.surfaceSlimeChance() : Float.NaN);
		String settings = c.radius + "|" + c.showPassive + "|" + c.mobFilter + "|" + seed;
		if (!env.equals(lastEnv) || !settings.equals(lastSettings)) {
			lastEnv = env;
			lastSettings = settings;
			CACHE.values().forEach(Scanner::dirty);
			job = null;
			STATS.fillStart = t0;
			STATS.lastFillNanos = -1;
		}
		applyDirtyEvents(level);

		BlockPos center = mc.player.blockPosition();
		net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
		int r = c.radius;
		int scx = SectionPos.blockToSectionCoord(center.getX());
		int scy = SectionPos.blockToSectionCoord(center.getY());
		int scz = SectionPos.blockToSectionCoord(center.getZ());
		int rs = (r >> 4) + 1;
		int minSy = level.getMinSectionY();
		int maxSy = level.getMaxSectionY();
		List<long[]> work = new ArrayList<>();
		LongOpenHashSet keep = new LongOpenHashSet();
		for (int sx = scx - rs; sx <= scx + rs; sx++) {
			for (int sz = scz - rs; sz <= scz + rs; sz++) {
				for (int sy = Math.max(minSy, scy - rs); sy <= Math.min(maxSy, scy + rs); sy++) {
					if (!touchesRadius(center, sx, sy, sz, r)) {
						continue;
					}
					long key = SectionPos.asLong(sx, sy, sz);
					keep.add(key);
					Section s = CACHE.get(key);
					if (s == null || s.dirty && ticks - s.dirtyAt >= DEBOUNCE_TICKS) {
						long dx = sx - scx;
						long dy = sy - scy;
						long dz = sz - scz;
						long d2 = dx * dx + dy * dy + dz * dz;
						// sections in front of the camera first: behind counts as up to 2.5x farther
						double len = Math.sqrt(d2);
						double dot = len == 0 ? 1 : (dx * look.x + dy * look.y + dz * look.z) / len;
						long prio = Math.round(d2 * 16 * (1.75 - 0.75 * dot));
						work.add(new long[] {prio, key});
					}
				}
			}
		}
		CACHE.keySet().removeIf(k -> !keep.contains(k));
		work.sort((a, b) -> Long.compare(a[0], b[0]));

		// while a lot is waiting (joining, turning the overlay on, a time change) spend up to 3x the budget
		long budget = c.scanBudgetMs * 1_000_000L * (STATS.queue > 16 ? 3 : 1);
		int waiting = 0;
		int done = 0;
		int next = 0;
		while (System.nanoTime() - t0 < budget) {
			if (job == null) {
				if (next >= work.size()) {
					break;
				}
				long key = work.get(next++)[1];
				int res = start(level, key, env);
				if (res < 0) {
					waiting++;
				} else if (job == null) {
					done++; // nothing to evaluate, finished at once
				}
				continue;
			}
			if (!keep.contains(job.key)) {
				job = null;
				continue;
			}
			if (run(job, c, t0 + budget)) {
				finish(job);
				job = null;
				done++;
			}
		}
		STATS.queue = Math.max(0, work.size() - done) + (job != null ? 1 : 0) + debouncing();
		STATS.waitingForServer = waiting;
		long took = System.nanoTime() - t0;
		long cpu = CPU.getCurrentThreadCpuTime() - cpu0;
		STATS.cpuNanos += cpu;
		if (!work.isEmpty()) {
			STATS.workTicks++;
		}
		STATS.maxTickCpuNanos = Math.max(STATS.maxTickCpuNanos, cpu);
		STATS.lastTickNanos = took;
		STATS.maxTickNanos = Math.max(STATS.maxTickNanos, took);
		if (STATS.queue == 0 && STATS.fillStart >= 0) {
			STATS.lastFillNanos = System.nanoTime() - STATS.fillStart;
			STATS.fillStart = -1;
			SpawnsightClient.LOG.info("Spawnsight: radius {} filled in {} ms ({} sections cached)", r, STATS.lastFillNanos / 1_000_000, CACHE.size());
		}
	}

	private static boolean touchesRadius(BlockPos c, int sx, int sy, int sz, int r) {
		// distance from the player to the nearest point of the section box
		int x0 = sx << 4;
		int y0 = sy << 4;
		int z0 = sz << 4;
		long dx = Math.max(0, Math.max(x0 - c.getX(), c.getX() - (x0 + 15)));
		long dy = Math.max(0, Math.max(y0 - c.getY(), c.getY() - (y0 + 15)));
		long dz = Math.max(0, Math.max(z0 - c.getZ(), c.getZ() - (z0 + 15)));
		return dx * dx + dy * dy + dz * dz <= (long) r * r;
	}

	private static void applyDirtyEvents(ClientLevel level) {
		long[] events;
		synchronized (DIRTY_EVENTS) {
			if (DIRTY_EVENTS.isEmpty()) {
				return;
			}
			events = DIRTY_EVENTS.toLongArray();
			DIRTY_EVENTS.clear();
		}
		boolean nether = level.dimension() == Level.NETHER;
		for (long k : events) {
			Section s = CACHE.get(k);
			if (s != null) {
				dirty(s);
				s.dirtyAt = ticks;
			}
			if (nether) {
				SERVER.forget(k); // the fortress rule depends on nether bricks below
			}
		}
	}

	/** A section being computed, possibly over several ticks. */
	private static final class Job {
		long key;
		Section section;
		int stamp;
		BitSet cand;
		SectionLists lists;
		int next;
		byte[] shade;
		int[] counts = new int[SpotEvaluator.Shade.values().length];
		long nanos;
		int bx;
		int by;
		int bz;
	}

	private static Job job;

	private static void dirty(Section s) {
		s.dirty = true;
		s.dirtyStamp++;
	}

	/**
	 * Starts a section: candidates and lists. Returns -1 while waiting for server lists, else the number
	 * of candidates. Sets {@link #job} when there is something to evaluate; finishes empty sections at once.
	 */
	private static int start(ClientLevel level, long key, Env env) {
		int sx = SectionPos.x(key);
		int sy = SectionPos.y(key);
		int sz = SectionPos.z(key);
		if (!level.hasChunk(sx, sz)) {
			return 0;
		}
		long t0 = System.nanoTime();
		Section s = CACHE.get(key);
		if (s == null) {
			s = new Section();
			s.dirty = true;
			CACHE.put(key, s);
		}
		Job j = new Job();
		j.key = key;
		j.section = s;
		j.stamp = s.dirtyStamp;
		j.cand = candidates(level, sx, sy, sz);
		j.bx = sx << 4;
		j.by = sy << 4;
		j.bz = sz << 4;
		if (!j.cand.isEmpty()) {
			if (ServerLists.available()) {
				j.lists = SERVER.get(key, j.cand);
				if (j.lists == null) {
					return -1;
				}
			} else {
				j.lists = table.get(level, key, j.cand);
			}
		}
		j.nanos = System.nanoTime() - t0;
		j.next = j.cand.nextSetBit(0);
		if (j.next < 0) {
			finish(j);
		} else {
			job = j;
		}
		return j.cand.cardinality();
	}

	/** Evaluates candidates until done (true) or the deadline passes (false). */
	private static boolean run(Job j, SpawnsightConfig c, long deadline) {
		long t0 = System.nanoTime();
		SectionLists l = j.lists;
		int n = 0;
		for (int i = j.next; i >= 0; i = j.cand.nextSetBit(i + 1)) {
			if ((++n & 7) == 0 && System.nanoTime() > deadline) {
				j.next = i;
				j.nanos += System.nanoTime() - t0;
				return false;
			}
			int idx = i;
			SpotEvaluator.Shade sh = SpotEvaluator.shade(world, lastEnv, (x, y, z, cat) -> l.at(idx, cat), Mobs::spec, c.showPassive, c.mobFilter,
					j.bx + (i & 15), j.by + (i >> 8), j.bz + ((i >> 4) & 15));
			int o = sh.ordinal();
			j.counts[o]++;
			if (o != 0) {
				if (j.shade == null) {
					j.shade = new byte[4096];
				}
				j.shade[i] = (byte) o;
			}
		}
		j.next = -1;
		j.nanos += System.nanoTime() - t0;
		return true;
	}

	private static void finish(Job j) {
		Section s = j.section;
		s.shade = j.shade;
		s.counts = j.counts;
		s.candidates = j.cand.cardinality();
		s.dirty = s.dirtyStamp != j.stamp; // marked again while computing: compute again
		s.version++;
		STATS.sectionsComputed++;
		STATS.nanosComputing += j.nanos;
		STATS.candidates += s.candidates;
		STATS.maxSectionNanos = Math.max(STATS.maxSectionNanos, j.nanos);
	}

	/** Blocks of the section that could hold a natural spawn (see {@link LevelWorld#candidate}). */
	static BitSet candidates(ClientLevel level, int sx, int sy, int sz) {
		BitSet out = new BitSet(4096);
		int idx = level.getSectionIndexFromSectionY(sy);
		if (idx < 0 || idx >= level.getSectionsCount()) {
			return out;
		}
		LevelChunk chunk = level.getChunk(sx, sz);
		LevelChunkSection sec = chunk.getSection(idx);
		LevelChunkSection below = idx > 0 ? chunk.getSection(idx - 1) : null;
		boolean onlyAir = sec.hasOnlyAir();
		if (onlyAir && (below == null || below.hasOnlyAir())) {
			return out;
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int ly = 0; ly < 16; ly++) {
			if (onlyAir && ly > 0) {
				break; // only the bottom layer can stand on the section below
			}
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					BlockState s = sec.getBlockState(lx, ly, lz);
					BlockState under = ly > 0 ? sec.getBlockState(lx, ly - 1, lz) : below != null ? below.getBlockState(lx, 15, lz) : air;
					p.set((sx << 4) + lx, (sy << 4) + ly, (sz << 4) + lz);
					if (LevelWorld.candidate(level, p, s, under)) {
						out.set(SectionLists.index(lx, ly, lz));
					}
				}
			}
		}
		return out;
	}

	private static int debouncing() {
		int n = 0;
		for (Section s : CACHE.values()) {
			if (s.dirty && ticks - s.dirtyAt < DEBOUNCE_TICKS) {
				n++;
			}
		}
		return n;
	}

	/** Counts of shades over all cached sections, for stats. */
	public static int[] shadeCounts() {
		int[] total = new int[SpotEvaluator.Shade.values().length];
		for (Section s : CACHE.values()) {
			for (int i = 0; i < total.length; i++) {
				total[i] += s.counts[i];
			}
		}
		return total;
	}

	public static int cachedSections() {
		return CACHE.size();
	}

	/** Iterates cached sections (for the renderer). */
	public static Long2ObjectOpenHashMap<Section> sections() {
		return CACHE;
	}
}
