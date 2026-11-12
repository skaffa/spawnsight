# Spawnsight: plan

Client-side Fabric mod for Minecraft 26.3. Answers "can a mob spawn on this block, and which one?" per
block, for spawn-proofing and mob-farm design. Shared rules: `deepseek-harness/default-workspace/mod-prompts/COMMON.md`.

- Name: **Spawnsight**, id `spawnsight`, package `dev.spawnsight`, command `/spawnsight`.
- Checked 2026-11-12: Modrinth slugs `spawnsight`, `spawn-sight`, `spawnsight-mod` are free (HTTP 404),
  Modrinth search for "spawnsight" has 0 hits, `github.com/skaffa/spawnsight` does not exist.
- Similar mods on Modrinth: "Spawn Overlay", "Mob Spawn Indicator", "Light Level Overlay", MiniHUD (light
  overlay + slime chunks). None of them explain *why*, show per-mob results, model time/weather, or read
  the exact rules from the integrated server. That is the gap Spawnsight fills.

All rules below were read in the decompiled 26.3 sources (`vire/.gradle/loom-cache/.../minecraft-common-*-26.3-sources.jar`)
and the 26.3 built-in data pack (`data/minecraft/...` in the 26.3 jar), on 2026-11-12. Class and method
names are 26.3 Mojang names.

---

## 1. How vanilla 26.3 spawns a mob naturally

`NaturalSpawner.spawnCategoryForPosition(category, level, chunk, start, ...)`, once per category per chunk
per tick (when the mob cap allows):

1. Pick a random column in the chunk and a random Y in `[minY, WORLD_SURFACE height + 1]`.
   Positions above the column's `WORLD_SURFACE` heightmap + 1 are never tried.
2. If the start block `isRedstoneConductor` (solid full block), stop.
3. Up to 3 groups x up to 4 tries; each try moves X/Z by `nextInt(6) - nextInt(6)`, same Y.
4. For each candidate `pos` (the block the mob's **feet** are in):
   - **Distance** (`isRightDistanceToPlayerAndSpawnPoint`): nearest player
     (`getNearestPlayer(x, y, z, -1, false)`) distance² from `(x+0.5, y, z+0.5)` must be **> 576 (24 blocks)**;
     the position must also be **≥ 24 blocks from the world respawn point** (same dimension); the chunk
     must be entity-ticking.
   - **Spawn list** (`mobsAt`): if category is MONSTER, the block below is `nether_bricks` and `pos` is
     inside a fortress structure: `NetherFortressStructure.FORTRESS_ENEMIES`. Otherwise
     `ChunkGenerator.getMobsAt`: the first structure at `pos` with a `spawn_overrides` entry for the
     category wins (box type `piece` = inside a piece, `full` = inside the start's bounding box);
     otherwise the environment attribute `gameplay/natural_mob_spawns` at `pos` (biome-driven). One entry is
     picked by weight per group, so a block that is valid for *some* entry can still be skipped.
   - `isValidSpawnPostitionForType`:
     - category not MISC;
     - if `!type.canSpawnFarFromPlayer()` and distance > category despawn distance (128; 64 for
       WATER_AMBIENT): no;
     - `type.canSummon()` and the entry is still in `mobsAt(pos)`;
     - `SpawnPlacements.isSpawnPositionOk` (placement type, §2);
     - `SpawnPlacements.checkSpawnRules` (peaceful check for `!isAllowedInPeaceful`, then the per-type
       predicate, §3);
     - `level.noCollision(type.getSpawnAABB(x+0.5, y, z+0.5))` (hitbox fits, §4).
   - `SpawnState.canSpawn`: spawn-potential budget (`spawn_costs`; only soul sand valley and warped
     forest use it in vanilla).
   - Then the mob is created and `isValidPositionForMob`: `mob.checkSpawnRules` (walk target value ≥ 0
     for pathfinder mobs, §3.4) and `mob.checkSpawnObstruction` (no liquid in the box + unobstructed by
     entities, with per-mob overrides).

Mob caps, the spawn-potential budget and the random column choice decide *how often*; Spawnsight decides
*whether it is possible at this block*. The "why" panel says this.

## 2. Placement types (`SpawnPlacementTypes`)

- **ON_GROUND** (almost every land mob): world border contains `pos`; block below
  `isValidSpawn(level, below, type)`; `pos` and `pos.above()` both pass
  `NaturalSpawner.isValidEmptySpawnBlock`:
  - not `isCollisionShapeFullBlock`;
  - not `isSignalSource` (buttons, levers, pressure plates, redstone wire, redstone torches, daylight
    detectors, observers...);
  - fluid state empty (no water/lava, waterlogged blocks count as fluid);
  - not in block tag `prevent_mob_spawning_inside` (vanilla: `#minecraft:rails`);
  - not `type.isBlockDangerous(state)`: burning blocks (`NodeEvaluator.isBurningBlock`: `#fire`, lava,
    magma block, lit campfire, lava cauldron) unless fire-immune, plus wither rose, sweet berry bush,
    cactus, powder snow (unless in the type's `immuneTo`).
  - So: **2 blocks of non-full, non-fluid, non-redstone space** is the placement rule for every
    ON_GROUND mob, including 1-block-tall mobs. Taller mobs are limited by the hitbox check (§4).
- **IN_WATER** (fish, squid, drowned, guardians, axolotls, dolphins, nautilus): `pos` fluid is water and
  `pos.above()` is not a redstone conductor.
- **IN_LAVA** (strider): `pos` fluid is lava.
- **NO_RESTRICTIONS**: evoker, fox, illusioner, panda, phantom, shulker, trader llama, vex, vindicator,
  warden. (Fox and panda are in biome lists; their predicates still check the floor.)

### `BlockState.isValidSpawn` (the floor)
Default: `isFaceSturdy(UP)` **and** `lightEmission < 14`. Overrides in `Blocks`:
- `never`: glass, tinted glass, stained glass, barrier, bedrock, all trapdoors (incl. iron), scaffolding,
  moving piston, chorus flower, repeater (and others with `Blocks::never`).
- `always`: soul sand, mud, carved pumpkin, jack o'lantern, redstone lamp.
- leaves / cherry & pale oak leaves / firefly bush: `ocelotOrParrot` only.
- ice, frosted ice: polar bear only. Magma block: fire-immune types only.
- So bottom slabs, carpets, glowstone/sea lanterns (emission 15), and upside-down stairs vs. normal stairs
  follow the default "sturdy top face" test. **Spawnsight calls `BlockState.isValidSpawn` itself** on the
  client, so every block (including modded blocks) is exact without a hand-written list.

## 3. Per-type predicates (`SpawnPlacements` registrations, 26.3)

Notation: `L_sky` = sky light at `pos`, `L_blk` = block light, `raw(d)` =
`max(L_sky - d, L_blk)` (`getMaxLocalRawBrightness(pos, d)`), `darken` = `level.getSkyDarken()`
= `15 - gameplay/sky_light_level`. Measured on the client (M1 self-test): clear day 0, full rain by day 3,
full thunderstorm by day 5, every night 11. Random draws are written as probabilities.

### 3.1 Monsters: `Monster.isDarkEnoughToSpawn`
1. `L_sky > nextInt(32)` fails: passes with chance `(32 - L_sky) / 32` (never certain on the surface by
   day *or* night: sky light, not effective light, is used here).
2. Dimension `monster_spawn_block_light_limit` (overworld 0, end 0, nether 15 = no limit):
   `L_blk > limit` fails. **Overworld/end: block light must be 0.**
3. `b = thundering ? raw(10) : raw(darken)`; passes if `b <= monster_spawn_light_level.sample()`:
   overworld uniform 0..7 (chance `(8 - b) / 8` for b ≤ 7), nether constant 7, end constant 15.

`checkMonsterSpawnRules` = (spawner ignores light) or dark enough, **and** `checkMobSpawnRules` = floor
`isValidSpawn`. Used by: zombie, zombie villager, zombie horse, skeleton, bogged, creeper, spider, cave
spider, enderman, witch, wither skeleton, creaking, giant, ravager, evoker/vindicator/illusioner/vex,
warden, wither.

- `checkSurfaceMonstersSpawnRules` (husk, parched, camel husk): + `canSeeSky(pos)`.
- `Stray.checkStraySpawnRules`: + `canSeeSky` above any powder snow stack.
- `checkAnyLightMonsterSpawnRules` = floor only (blaze, breeze, zoglin).
- `checkPatrollingMonsterSpawnRules` (pillager): `L_blk ≤ 8` + floor.
- `Drowned`: fluid below is water (else no); `pos` fluid water; dark enough; then
  `MORE_FREQUENT_DROWNED_SPAWNS` biome tag (rivers): 1/15, else 1/40 and `y < seaLevel - 5`.
- `Guardian` (+ elder): fluid at `pos` and below is water; not peaceful; `!canSeeSkyFromBelowWater(pos)`
  or 1/20. Only from monument overrides in practice.
- `Slime`: not peaceful. Two independent ways:
  - **swamp surface**: biome tag `allows_surface_slime_spawns` (swamp, mangrove swamp), `50 < y < 70`,
    `random < gameplay/surface_slime_spawn_chance` (from the **moon timeline**: full moon 0.5 → new moon
    0.0, steps of 0.125), and `raw(darken) ≤ nextInt(8)`, plus floor.
  - **slime chunk**: `WorldgenRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) == 0`,
    `y < 40`, 1/10, plus floor. No light rule at all.
- `MagmaCube`: not peaceful. `SulfurCube`: always true (placement + hitbox still apply).
- `Ghast`: not peaceful, 1/20, floor. `ZombifiedPiglin`: not peaceful, floor not nether wart block.
  `Piglin`, `Hoglin`: floor not nether wart block (no `isValidSpawn` check!).
- `Silverfish`, `Endermite`: floor + no player within 5 blocks.
- `Strider` (IN_LAVA): first non-lava block above the lava column is air.

### 3.2 Creatures / ambient / water
- `Animal.checkAnimalSpawnRules`: floor in `#animals_spawnable_on` (grass block) and
  `isBrightEnoughToSpawn` = `getRawBrightness(pos, 0) > 8` (**sky light at full day value**, so the
  result does not change with time of day).
- Same shape with own floor tag: armadillo, camel, mooshroom, fox, frog, goat, parrot, rabbit, wolf,
  polar bear (alternate floor tag in frozen ocean biomes), turtle (on sand, `y < seaLevel + 4`).
- `Ocelot`: 2/3 chance; obstruction override requires `y ≥ seaLevel` and grass or leaves below.
- `Bat`: below `WORLD_SURFACE` heightmap; 1/2; `raw(darken) ≤ nextInt(4)` (light ≤ 3); floor in
  `#bats_spawnable_on`.
- Water creatures/fish: `seaLevel - 13 ≤ y ≤ seaLevel`, water below, water block above (tropical fish:
  any height in `allows_tropical_fish_spawns_at_any_height`). Nautilus: `seaLevel-25 ≤ y ≤ seaLevel-5`.
  Glow squid: `y ≤ seaLevel - 33`, `getRawBrightness(pos, 0) == 0`, water. Axolotl: floor in
  `#axolotls_spawnable_on`.

### 3.3 Not block spawns
Phantoms (insomnia spawner), patrols, wandering traders, iron golems, cats in villages, raids, trial and
mob spawners, zombie reinforcements. The "why" panel names them as "not a natural block spawn".

### 3.4 Instance checks after creation (`isValidPositionForMob`)
- `PathfinderMob.checkSpawnRules`: `getWalkTargetValue(pos) ≥ 0`.
  - `Monster`: `-(lightMagic(pos) - 0.5) ≥ 0`, with
    `lightMagic = lerp(ambient_light, v/(4-3v), 1)`, `v = raw(darken)/15`, in float. Overworld (ambient 0):
    raw ≤ 11 (12 gives 0.50000006); nether (0.1): raw ≤ 11; end (0.25): raw ≤ 10. Redundant with §3.1 in
    the overworld and nether. In the end it matters for monsters without an override.
  - **Zombie horse and camel husk** are `AbstractHorse`/`Camel` (Animal) subclasses: they use the
    *animal* walk target (10 on grass blocks, else needs raw ≥ 12) together with monster darkness rules.
    Off grass both can never hold at once, so camel husks never spawn naturally on desert sand and
    zombie horses only on grass blocks. Spawnsight reports this as "never all at once".
  - Overrides: enderman and pillager return 0 (always ok); silverfish 10 on infestable blocks; guardian
    10 + light cost in water; animals 10 on grass, else the light formula; hoglin −1 near warped fungus /
    repellents; strider 10 in lava.
- `checkSpawnObstruction`: no liquid in the bounding box and no entity in the way. Overrides: water mobs,
  axolotl, guardian, strider only check entities; ravager only liquid; iron golem its own column check;
  warden also `noCollision`.

## 4. Hitbox fit
`level.noCollision(type.getSpawnAABB(x + 0.5, y, z + 0.5))`: the entity type's dimensions (spider 1.4 x
0.9, enderman 0.6 x 2.9, zombie 0.6 x 1.95, slime by size...) centred on the block, tested against real
collision shapes. A spider therefore needs the 3x3 area around the block free of collision at its height;
an enderman needs 3 blocks of collision-free space. Spawnsight calls the same method on the client
`Level`, so slabs, fences, carpets etc. are exact. Slimes and magma cubes have `spawnDimensionsScale(4)`:
their spawn box is 2.08 x 2.08 (sulfur cube: 2x, 0.98); the liquid check uses the new mob's 0.52 box
(size 1, checked in M1).

## 5. Distance and spheres
- No spawn within **24 blocks** (distance² > 576, from block-bottom-centre to the nearest player's feet)
  and within 24 blocks of the world spawn point.
- Despawn: `Mob.checkDespawn` per category: beyond `despawnDistance` (128, WATER_AMBIENT 64): instant
  despawn; beyond `noDespawnDistance` 32 after 600 idle ticks: 1/800 per tick. MONSTERs cannot spawn
  beyond 128 (they cannot spawn far from the player).
- The overlay normally shows the player-independent answer (what spawns here when you stand elsewhere,
  what the spawn-proofer needs). Blocks inside the 24-block sphere get a hatched/dim variant of their
  colour ("would spawn if you were ≥ 24 blocks away"); a toggle "use my position" applies the rule
  strictly. Spheres 24/32/128 (+ 64 for water ambient, optional) are drawn as outlines on demand.

## 6. Colours
Each candidate mob gets a deterministic result plus a "light/random" part:
- **Red**: a hostile mob is possible **now** (current `darken`, thunder, moon phase, difficulty).
- **Yellow**: not possible now but possible at some time: night (`darken` 11), thunderstorm (`raw(10)`),
  another moon phase (surface slimes), or only with a small random chance the user filters out (optional
  threshold).
- **Green** (optional layer): a passive/ambient/water mob is possible.
- Nothing: no rule passes for any mob in the active spawn list.
The "why" panel lists every candidate with its chance factors (e.g. "zombie: sky 3 → 91 %, light 0 ≤
0..7 → 100 %, weight 95/535") or the **first failing rule** in vanilla order ("block light 3 > 0",
"bottom slab: top face not sturdy", "glass: never a spawn floor", "only 1 block of space: needs 2",
"spider hitbox 1.4 wide hits the wall", "within 24 blocks of you", "not in the spawn list of this biome").

## 7. What the client can and cannot know (checked in 26.3)

| Input | Client? | Source |
|---|---|---|
| Blocks, collision shapes, fluids, block tags | yes | `ClientLevel` |
| Block light, sky light | yes | client light engine (synced) |
| `darken` / time of day | yes | `gameplay/sky_light_level` is `syncable()`, `Level.updateSkyBrightness` runs on the client |
| Thunder, rain | yes | game events |
| Moon phase | yes | `visual/moon_phase` is syncable |
| Difficulty, sea level, world border | yes | synced |
| World spawn point | yes | `SetDefaultSpawnPositionPacket` |
| Biome per block | yes | chunk biome data |
| Biome spawn lists `gameplay/natural_mob_spawns` | **no** | not `syncable()`; `Biome.NETWORK_CODEC` filters to syncable attributes and drops generation settings |
| `gameplay/surface_slime_spawn_chance` | **no** | not syncable (derived from moon phase with the vanilla moon timeline) |
| Biome tags | yes | checked in M1: swamp has `#allows_surface_slime_spawns` on the client |
| Structure starts/pieces | **no** | server only |
| World seed (slime chunks) | **no** | server only |
| Mob caps, spawn potential, entity-ticking chunks, gamerules | no | server only |

- **Singleplayer / LAN host**: read the integrated server's `ServerLevel` for the exact spawn list
  (`ChunkGenerator.getMobsAt` incl. structures and the fortress rule), the seed, `doMobSpawning`, and
  datapack changes. Server reads happen on the server thread (`server.execute`) and are cached per chunk.
- **Multiplayer**: a vanilla table generated at build time from the 26.3 data pack (biome →
  category → entries with weights; structure overrides; moon timeline values) as a small JSON in the
  jar. The UI says "vanilla tables: datapacks and server plugins can change spawns". Structures:
  unknown. Heuristic only for the fortress (nether bricks below + nether), shown as "assumed". Slime
  chunks: unknown until `/spawnsight seed <seed>`; then exact. Structure-dependent blocks are labelled
  "depends on structures (unknown on servers)".

## 8. Architecture
- `core/` (Minecraft-free, JUnit 5):
  - `rules/`: the rule chain as data (`Grid` interface: block facts per position; `Env`: light, darken,
    thunder, moon, difficulty, dimension light rules, sea level). Returns `Verdict` = first failing rule
    or a pass with chance factors (now / night / thunder / best moon). Tested on a fake grid.
  - `spheres/`: distance and sphere outline maths. `table/`: spawn table model, JSON reader, lookup.
  - `config/`: `ConfigStore` copied from Vire.
- `client/`:
  - `probe/`: fills the `Grid` from `ClientLevel` using vanilla methods (`isValidSpawn`,
    `isValidEmptySpawnBlock`, `getSpawnAABB` + `noCollision`, `isBlockDangerous`, tags).
  - `source/`: `ServerPeek` (integrated server, exact) and `TableSource` (vanilla table + seed).
  - `scan/`: per-section result cache (16³, a few bits per block per category + per-mob mask for the
    farm filter); dirty on block changes and light updates; work queue with a per-tick time budget,
    nearest sections first. Time/weather/moon changes recolour from stored chance factors without a
    rescan.
  - `render/`: batched quads on block tops (one buffer per section, rebuilt when dirty), sphere
    outlines; `hud/`: the aimed-block "why" line and the detail panel.
- Keys (all rebindable): **U** overlay on/off, **I** "why" panel for the aimed block (a one-line HUD
  hint shows automatically while the overlay is on), **Y** spheres. (H is Vire/Ortho, J/N/M/B/K/V are
  Vire.) Farm filter, green layer, radius: settings screen and `/spawnsight mob <id>|all`,
  `/spawnsight radius <n>`, `/spawnsight seed <seed>|clear`, `/spawnsight config`, `/spawnsight help`.
- Files: `config/spawnsight/config.json`, `config/spawnsight/seeds.json` (seed per server address).

## 9. Performance target
Radius 32 (a 65 x 65 x 65 cube, ≈ 275 k blocks, ≈ 70 sections). Skip sections that are all air or all
full blocks (`LevelChunkSection.hasOnlyAir`, `maybeHas`). Budget 2 ms per client tick for scanning;
initial fill under 1 s; rescan only dirty sections. Rendering: one cached mesh per section, no per-frame
allocation. Report: full-scan time, ms per dirty section, frame time with overlay off/on at radius 16
and 32, memory per section. Stop and report if radius 32 costs more than 1 ms per frame.

## 10. Testing
- Unit tests: rule chain on a fake grid (slabs, glass, carpets, 1-high and 2-high gaps, light levels,
  spider width, enderman height, water, rails, buttons), chance maths, sphere maths, table lookup and
  JSON round trip, slime chunk maths against known seed/chunk pairs.
- In-game self-test `-Pselftest=rig` (singleplayer, integrated server): builds a rig at a fixed location
  (dark room with stone, bottom/top slabs, glass, carpets, rails, buttons, 1-high and 2-high gaps, a lit
  corner, a spider-width test, a water pool, a slime-chunk check with the known seed), then:
  1. **Exact comparison**: for every rig block and mob in the list, run vanilla's own server checks
     (`SpawnPlacements.isSpawnPositionOk`, `SpawnPlacements.checkSpawnRules` with a best-case
     `RandomSource` stub that returns the minimum, `noCollision(getSpawnAABB)`, `Mob.checkSpawnRules`
     on a created mob) and compare with Spawnsight's "possible at some time" answer. Must be identical.
  2. **Natural spawning**: night, player teleported > 24 blocks away, other mobs killed, let the
     server tick (`/tick sprint`); every naturally spawned mob in the rig must stand on a red/yellow
     block whose list contains its type. Pass by condition, not tick count.
  3. **Table mode**: rerun Spawnsight in "multiplayer mode" on the same rig; must match except blocks
     labelled "unknown".
- Multiplayer smoke test on `run-server/` (copied from Gauge/Vire): overlay works, labels say "vanilla
  table", `/spawnsight seed` turns slime chunks exact.
- Screenshots of the overlay, the "why" panel and the spheres, looked at.

## 11. Milestones (stop and report after each)
- **M0 Skeleton**: repo (`main`), Loom/Fabric versions from COMMON.md, `fabric.mod.json` (client),
  ConfigStore + tests, keys, `/spawnsight help|config`, settings screen + Mod Menu, CI workflow, icon
  script. Build passes, runClient loads.
- **M1 Rule engine** (done 2026-11-12): `core/rules` with all §2–§5 rules, chance maths, unit tests on the fake grid.
  Spawn table generator (Gradle task reading the 26.3 jar's data pack) + lookup tests. Verify biome tag
  sync and slime size in-game.
- **M2 Scanner + sources**: client probe, ServerPeek (exact singleplayer), section cache, dirty tracking
  (block + light updates), tick budget. Measure numbers.
- **M3 Overlay and UI**: section meshes, red/yellow/green, hatched near-player variant, spheres, HUD
  line, "why" panel, farm filter. Screenshots.
- **M4 Multiplayer**: TableSource, `/spawnsight seed`, uncertainty labels, fortress heuristic; test on
  run-server.
- **M5 Self-test**: rig, exact comparison, natural-spawn check, table-mode check; all PASS.
- **M6 Release prep**: README, CHANGELOG, `media/modrinth-description.md`, gallery 1600x900 +
  `captions.md`, icon. Ask before creating the GitHub repo, pushing, tagging.

## 12. Open questions for the user
1. Spawn table for multiplayer: the prompt asks for a build-time JSON. The 26.3 client jar already
   contains the vanilla data pack (`data/minecraft/worldgen/biome/*.json`), so the mod could also parse
   it at runtime with no generated file. Build-time JSON is planned (smaller, simpler, testable); say if
   you prefer runtime parsing.
2. Default overlay rule for the 24-block sphere: planned as "show what spawns when you are away, hatch
   blocks within 24". Strict mode is a toggle.
3. Keys U / I / Y: OK?
