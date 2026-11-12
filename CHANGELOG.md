# Changelog

## 0.1.0+26.3

First release, for Minecraft 26.3 with Fabric.

- **Overlay** (`U`): red where a hostile mob can spawn now, yellow where only at night, in a
  thunderstorm or another moon phase, purple where only slimes could (seed unknown), green where a
  passive mob can. Blocks within 24 blocks of you are drawn smaller and fainter.
- **Why panel** (`I`) and a hint under the crosshair: every mob in the spawn list with its chance per
  attempt or the first rule that stops it. `/spawnsight why` prints it in chat.
- **Spheres** (`Y`): 24, 32 and 128 blocks around you.
- **Farm mode**: `/spawnsight mob <entity>`.
- The 26.3 natural spawn rules from the game's code: placement types, spawn floors, space and hitboxes,
  light by dimension, time and weather, biome lists, structure spawns, slime chunks, surface slimes and
  the moon, and the checks a created mob does.
- Exact in singleplayer (lists, structures, data packs and seed from the integrated server); vanilla
  spawn tables on servers, with `/spawnsight seed` for slime chunks and clear labels for what is not
  known.
- Settings screen (Mod Menu or `/spawnsight config`) with tooltips.
