**Can a mob spawn here, and which one?** Spawnsight colours every block around you by what can spawn on it, tells you why or why not for the block you look at, and shows the spawn and despawn spheres. For spawn-proofing a base and for designing mob farms.

Spawnsight is client-side only. It needs nothing on the server.

## See where mobs spawn

Press **U** for the overlay:

- **Red**: a hostile mob can spawn here now.
- **Yellow**: only sometimes: at night, in a thunderstorm or in another moon phase.
- **Purple**: only slimes, if this is a slime chunk (the seed is unknown).
- **Green**: a passive mob can spawn here (optional).

Nothing spawns within 24 blocks of a player, so blocks that close are drawn smaller and fainter: they show what spawns there when you are away.

## Know why

Look at a block: a line under the crosshair says who can spawn there. Press **I** for the full list, every mob with its chance per spawn attempt or the rule that stops it:

- `block light 3 > 0`
- `Stone Slab: top face is not solid, not a spawn floor`
- `only 1 block of space: mobs need 2`
- `no room for the spider (1.40 wide)`
- `too bright at night: light 9 > 7`

Mobs that share a reason are grouped, so the answer is short.

## Build farms

- **Spheres** (**Y**): 24 blocks (no spawning closer), 32 (random despawn beyond) and 128 (instant despawn).
- **Farm mode**: `/spawnsight mob minecraft:enderman` shows only where endermen can spawn.

## Exact rules

Spawnsight uses the natural spawn rules of 26.3 from the game's code: spawn floors (slabs, glass, leaves, trapdoors, glowing blocks, modded blocks), two blocks of free space, each mob's real hitbox, light by dimension, time of day, thunderstorms, the moon for swamp slimes, biome spawn lists, structure spawns and slime chunks. A built-in test compares it with the game's own spawn checks on a test rig and with mobs the server really spawns: no differences.

- **Singleplayer**: exact, including structures, data packs and slime chunks.
- **On servers**: the server does not send the spawn lists, so Spawnsight uses the vanilla 26.3 tables. Data packs and plugins that change spawns, and structure spawns, are not known; the overlay says so. Enter the world seed with `/spawnsight seed <seed>` to see slime chunks.

## Good to know

- It shows whether a spawn is possible. How many mobs you get also depends on mob caps and luck.
- Entities standing in the way are not checked.
- Rescans only what changes and spreads the work over ticks; radius and time budget are in the settings.
- **Settings** in Mod Menu or with `/spawnsight config`. `/spawnsight help` lists every command.
- Files live in `config/spawnsight/`.

Requires Fabric API. Mod Menu is optional.
