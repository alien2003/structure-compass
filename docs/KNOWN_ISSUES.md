# Known issues and limitations

What is not implemented, not verified, or behaves differently from what one might expect.

## Search

- **Predicted position and Y.** For structures that are not generated yet, the reported X/Z is the generation
  point of the structure's start piece (what the game computes before building it) and Y is shown as `~`. The real
  centre, Y and boxes are used as soon as the structure's start chunk is loaded (checked every second around the
  player).
- **Prediction vs. reality.** Predictions use the same code path as world generation, but:
  - a start can be "valid" for the placement while the real start ends up empty (e.g. a jigsaw structure that
    places zero pieces) — such a prediction is dropped once its chunk is loaded;
  - chunks generated earlier by another game version, another mod set or before a datapack was added can differ —
    again corrected when the chunk is loaded;
  - mods that replace `ChunkGenerator#createStructures` or the structure state with their own logic are not
    simulated.
- **Modded placement types.** Placements that are neither random spread nor concentric rings are searched chunk by
  chunk up to 96 chunks (~1500 blocks) away. Structures of any kind that exist in loaded chunks around the player are
  added as well.
- **Other dimensions in the screen** are searched around the equivalent position (Nether coordinates × 8 / ÷ 8);
  the HUD and the world highlights always show the dimension you are in.
- **Large radii.** The radius goes up to 32 000 blocks. The search is off-thread and does not affect TPS/FPS, but
  with a very large radius and many rare structure types the first results for rare types can take a while.
- **Distances** are horizontal (X/Z) distances in blocks (= meters).

## Multiplayer

- Structure search is a **single-player** feature: on a dedicated server the server part answers that searching is
  disabled. LAN guests connected to a host's integrated server are served too, but this was not tested.
- On servers without the mod the client stays silent apart from one chat message per connection.

## Rendering / UI

- Very large structures (mineshafts) are drawn with up to 256 piece boxes each and at most 2048 piece boxes per
  frame; outline boxes are limited to `boxDistance` (192 blocks by default).
- When several labels would overlap on screen, only the nearest one is drawn (the beams are still drawn).
- The compass bar moves down below vanilla boss/raid bars; other mods' top-of-screen HUD elements may still
  overlap it (its width and range are configurable, and it can be turned off).
- The teleport button (cheats only) asks the server to move you to a safe spot at the surface above the structure
  (or to an air gap under the Nether roof); it does not try to put you inside underground structures.
- Not tested with shader packs (Iris/Oculus), with Fabulous graphics, with GUI-heavy mods, or with very small GUI
  scales/resolutions.
- The tracked target is not saved between game sessions. If you track a predicted structure that later turns out not to
  exist (e.g. an old chunk generated without it), the tracking keeps pointing there until you untrack it (`G` or the
  screen).
- The HUD and world rendering do not allocate; the remaining per-frame allocations happen inside vanilla's font
  renderer.

## Verification status

- Automated: JUnit tests for the math/formatting helpers; 8 game tests on the headless game test server (seed 0)
  comparing the locator with vanilla `/locate` semantics and vanilla world generation in all three dimensions; a
  dev-client smoke test (`./dev runClientSmokeTest`) that creates a world, flies to a generated structure and takes
  screenshots of the HUD, world highlights, structure screen (English and Russian) and the Nether HUD.
- Visually checked only through those automated screenshots; hands-on checks still to do are listed in
  [TESTING.md](TESTING.md) (config screen sections, key bindings, teleport button, a server without the mod, modded
  structures).
- The dedicated-server path (search disabled + message) was not run, because starting a dedicated server requires
  accepting the Minecraft EULA, which was not done on the user's behalf.
