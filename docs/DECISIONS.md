# Decisions log

Autonomous decisions taken while building Structure Compass, with the reason for each.

## Toolchain and versions

| Item | Decision | Why |
|---|---|---|
| Minecraft | 1.21.11 | Requested. |
| NeoForge | **21.11.48** | Newest non-beta `21.11.x` in `https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml` on 2026-11-09 (the 21.11.0–21.11.41 builds are `-beta`). |
| ModDevGradle | **2.0.148** | Latest release in the MDG maven metadata; also what the official 1.21.11 MDK uses. |
| Parchment | **2025.12.20** for 1.21.11 | The only non-snapshot Parchment release for 1.21.11 on `maven.parchmentmc.org`. |
| Gradle | 9.2.1 (wrapper from the official MDK) | Kept identical to the official `NeoForgeMDKs/MDK-1.21.11-ModDevGradle` template the project was based on. |
| Java | Temurin 21 via mise (`java = "temurin-21"`, resolved to 21.0.13+9) | Requested; Minecraft 1.21.11 runs on Java 21. |
| Package | `dev.alien2003.structurecompass` | GitHub login from `gh api user` is `alien2003`. |
| Jar name | `structurecompass-neoforge-1.21.11-<version>.jar` | Makes loader and game version obvious on the release page. |

## Keeping everything inside the project folder

* `mise.toml` pins the JDK and sets `GRADLE_USER_HOME={{config_root}}/.gradle-home` in `[env]`, so the Gradle
  distribution, dependency caches, NeoForm/NeoForge artifacts and Minecraft assets all live in `.gradle-home/`.
* `./dev` (bash) and `dev.cmd` (Windows) export `MISE_DATA_DIR`, `MISE_CACHE_DIR`, `MISE_STATE_DIR`,
  `MISE_TMP_DIR` and `MISE_CONFIG_DIR` pointing into `.tools/mise/…` (names verified in the mise "directories"
  documentation). `MISE_CONFIG_DIR` is redirected as well so a user's global mise config can neither leak in nor
  be written. `MISE_TRUSTED_CONFIG_PATHS` is set to the project root so mise never prompts or writes a trust entry
  elsewhere.
* The wrappers also point `TMP`/`TEMP` (Windows) and `TMPDIR` (Unix) at `.tools/tmp`, so the JVMs started by the
  build (Gradle daemon, game runs) write their temporary files into the project too.
* If `mise` is not on `PATH`, the wrappers download a private copy into `.tools/bin` (no global install).
* Git identity is set in the repository's local `.git/config` only (`alien2003` /
  `11610652+alien2003@users.noreply.github.com`, the GitHub no-reply address) because no global git identity was
  configured and editing global settings is not allowed.
* The official MDK was cloned once into the agent session's scratch directory to copy its files; that directory is
  outside the project and is removed at the end (see "Outside-the-project audit" below).

## Architecture

* **Packages.** `common` (records + pure math, no client classes), `network` (payloads, registration),
  `server` (locator, scan service, game tests), `client` (everything client-only, loaded only from the
  `@Mod(dist = CLIENT)` entry point `StructureCompassClient`). The common entry point never references client
  classes, so the mod is safe on a dedicated server.
* **Search algorithm.** Instead of calling vanilla `/locate` (it blocks the server thread, reads region files and
  generates chunks to `STRUCTURE_STARTS`) the locator re-implements the placement walk and validates each candidate
  chunk the same way `ChunkGenerator#createStructures` does, including frequency reduction, exclusion zones and the
  weighted choice inside structure sets with several structures (vanilla `/locate` ignores the latter two and can
  e.g. point a fortress at a bastion spot). Validation uses `Structure#findValidGenerationPoint`, which vanilla runs
  concurrently on world-generation threads, so it is safe on our worker. Results are the true nearest N (vanilla
  `/locate` returns the first hit in ring order).
* **Threading.** One daemon worker thread (`StructureCompass-Locator`, low priority) per server. The server thread
  only drains a lock-free queue (at most 48 type results per player per tick) and reads loaded chunks with
  `getChunkNow` (never loads). A snapshot of the generator state is created on the server thread
  (`ensureStructuresGenerated` is not thread-safe). Outcomes are cached per (structure set, chunk); the cache is
  bounded (200 000 entries per dimension) and cleared on `/reload` (`TagsUpdatedEvent`, biome tags can change).
* **Real boxes.** For loaded start chunks the real `StructureStart` replaces the prediction (overall box = union of
  piece boxes, because `StructureStart#getBoundingBox` is inflated by 12 blocks for terrain adaptation). A loaded
  chunk without the predicted start drops the prediction and re-locates that type. Starts found in loaded chunks
  around the player that were not predicted are merged in.
* **Re-scan triggers.** Dimension change, moving more than `rescanDistance` (default 128) from the last scan
  centre, changed settings, the "Search again" button, `/reload`.
* **Distances** are horizontal; the HUD/beam target is the generation point (predicted) or the box centre
  (generated). Y is only shown when real.
* **Networking.** Optional payloads (`registrar.optional()`), protocol version "1". The server greets clients that
  have the channel; dedicated servers answer `enabled = false`. The client checks `hasChannel` on login and stays
  silent (one chat message) on servers without the mod. Clientbound handlers are registered with
  `RegisterClientPayloadHandlersEvent` (no client classes in common code).
* **Dedicated servers** do not run searches: the mod is a single-player helper and a server-wide structure finder
  would be a cheat for every player.
* **Rendering.** World highlights are drawn in `RenderLevelStageEvent.AfterLevel` (after compositing, before the
  hand) with one custom position-colour quad pipeline (`NO_DEPTH_TEST`, no cull, registered via
  `RegisterRenderPipelinesEvent`). Box edges are thin camera-facing quads of constant on-screen width instead of
  vanilla lines, because the line shader applies fog (boxes would turn fog-coloured past the render distance) and the
  quad pipeline does not; edges are clipped at the near plane like vanilla gizmos. Labels use vanilla see-through text;
  overlapping labels are thinned out nearest-first and drawn farthest-first. Far markers are pulled radially inside the
  far plane. All per-frame data is precomputed on the client tick (`NearbyView`, index sort with stable row slots),
  vertices are written camera-relative without per-vertex matrix math, so the frame path does not allocate.
* **Compass bar** sits below vanilla boss/raid bars (their position comes from NeoForge's
  `CustomizeGuiOverlayEvent.BossEventProgress`).
* **"Enabled types"** are stored as a blacklist (`disabledTypes`) so structures added by new mods/datapacks show up
  automatically.
* **Key bindings** `K` (screen), `H` (HUD), `J` (world highlights), `G` (next target) — unused by vanilla 1.21.11,
  all rebindable, category "Structure Compass".
* **Teleport button** only when the player has permission level 2 (`Permissions.COMMANDS_GAMEMASTER`). It sends a small
  request; the server re-checks the permission, finds a safe Y at the structure's X/Z (`MOTION_BLOCKING` heightmap, or an
  air gap with a sturdy floor under a ceiling) and runs vanilla `/execute in <dimension> run tp …`. (A client-side
  `/spreadplayers` was tried first, but it refuses liquid and fails over oceans and lava; `/tp` to a box centre can put
  the player inside blocks.)
* **Structure names.** `structurecompass.structure.<namespace>.<path>` translations for all 34 vanilla structures
  (en/ru); other mods' structures use `structure.<namespace>.<path>` if they provide it, else a humanized id
  ("deep_dark_tower" → "Deep Dark Tower", plus the mod id in the screen).

## Testing

* **Game test server limits.** NeoForge's game test server always uses seed 0, a flat overworld (only villages and
  strongholds) and `generateStructures = false`, so vanilla `/locate` returns nothing there. The tests therefore use a
  line-by-line port of vanilla `ChunkGenerator#findNearestMapStructure` in which "load the chunk and read its start"
  is replaced by vanilla `ChunkGenerator#createStructures` on a detached `ProtoChunk` — i.e. what `/locate` would
  answer on a world with structures enabled. Covered: plains villages and strongholds (overworld), fortress, bastion,
  nether fossil, nether ruined portal (Nether), end cities (End), from two positions each; plus a brute-force
  completeness check, box conversion, payload codecs and an off-thread full scan.
* **`neoforge.enabledGameTestNamespaces`** from the MDK no longer exists in NeoForge 21.11; the game test run
  passes `--tests structurecompass:*` instead.
* **Headless run** = `runGameTestServer` (exits with the number of failed tests; also run in CI). A dedicated server
  run was not used because it requires accepting the Minecraft EULA in `eula.txt`, which was not done on the user's
  behalf.
* **Client.** The desktop-automation tool could not attach to the game window, so a dev-only smoke test
  (`runClientSmokeTest`, enabled by `-Dstructurecompass.smoketest=true`) drives the client itself: it creates a world
  (seed 20261109), waits for the search, flies to a generated structure and saves screenshots. The screenshots in
  `docs/images` come from it.
* Game test and smoke test classes ship in the jar but are registered only when `FMLEnvironment.isProduction()` is
  false (development runs).

## CI / release

* `build.yml`: Temurin 21 via `actions/setup-java@v6`, Gradle cache via `gradle/actions/setup-gradle@v6`,
  `./gradlew build` + `./gradlew runGameTestServer`, jar uploaded as an artifact. CI uses the Gradle wrapper
  directly (the runner already has Java), the local workflow uses `./dev`.
* `release.yml`: on `v*` tags, checks that the tag matches `mod_version`, builds and tests, extracts the matching
  section of `CHANGELOG.md` and publishes the GitHub release with the jar via `gh release create`.

## Review

* After the first complete version, an automated multi-agent review (4 lenses: server threading, client logic,
  rendering/performance, dist-safety/build/tests) produced 32 findings; each was checked by an independent skeptic
  agent. 28 were confirmed and fixed (e.g. per-type failure isolation in the worker, re-locating disproved predictions,
  modelling vanilla's "skip a set whose structure already started here", generation-point slack for jigsaw
  structures, cache without cheap rejections and with persistent invalidations, unbounded/stable `NearbyView`,
  language caches keyed on the loaded `Language`, fog-free box edges, boss-bar offset, safer teleport). 4 were
  rejected as not reproducible or intended.

## Outside-the-project audit

A snapshot of `%USERPROFILE%`, `%LOCALAPPDATA%`, `%APPDATA%`, `LocalLow` and `%TEMP%` was taken at the start and
compared at the end (`.tools/audit-outside.ps1`).

* **Not created:** `~/.gradle`, `~/.m2`, `~/.minecraft`, `%LOCALAPPDATA%\mise`, `~/.local/share/mise`,
  `~/.config/mise`, `HKCU\Software\JavaSoft`. The JDK (`.tools/mise/data/installs/java`), the Gradle distribution,
  all dependency/Minecraft caches (`.gradle-home`) and all game runs (`run/`, `run/gametest`, `run/smoketest`) are
  inside the project.
* **Created outside and removed again:**
  * `%TEMP%\mise\latest-version.releases-v1` — mise's update check from the very first `mise --version` call, made
    before the `MISE_*` variables were set. Deleted.
  * `%TEMP%\hsperfdata_user` (empty) — JVM performance-data folder created by `javap` calls of the research
    sub-agents, which ran the JDK directly instead of through the wrappers. Deleted. Builds through `./dev` /
    `dev.cmd` do not create it (TMP/TEMP redirect on Windows, `JAVA_TOOL_OPTIONS=-XX:-UsePerfData` on Linux/macOS).
  * The agent's own session scratch directory (`%TEMP%\claude\…\scratchpad`: the MDK clone, the baseline snapshot,
    image crops). Deleted at the end.
* **Pre-existing, not ours:** `~/.local/state/mise` (created 2026-10-26), Visual Studio installer logs
  (`%TEMP%\dd_BackgroundDownload_*`), KDE Connect and other applications' temp files.
* **Intended external effects:** the public GitHub repository `alien2003/structure-compass`, its Actions runs and the
  `v1.0.0` release. `gh` used its existing login (`%APPDATA%\GitHub CLI`, pre-existing).
* CI additionally checks on every push that `./dev build` on a clean Linux clone writes nothing to `$HOME` outside
  the checkout.
