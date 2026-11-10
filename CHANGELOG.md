# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-11-10

First release for Minecraft 1.21.11 / NeoForge 21.11.48+.

### Added
- Structure search for **every structure type in the world's registry** (vanilla, datapacks, other mods), per
  dimension, several nearest instances per type (default 3) within a configurable radius (default 4000 blocks).
- Search runs on a single low-priority background thread and mirrors vanilla world generation
  (`ChunkGenerator#createStructures`): random-spread and concentric-ring placements, frequency reduction,
  exclusion zones and the weighted choice inside structure sets with several structures. It never loads or
  generates chunks, caches results per (structure set, chunk) and re-searches after moving `rescanDistance`
  blocks or changing dimension.
- Real bounding boxes (overall and per piece) for structures whose start chunk is loaded; wrong predictions are
  dropped as soon as the chunk is loaded; structures that exist in loaded chunks but were not predicted are added.
- HUD panel with the nearest structures: color, localized name, distance in meters, X/Y/Z (Y shows `~` until the
  structure is generated) and a direction arrow; a compass bar with structure markers; a large arrow for the
  tracked structure.
- In-world highlights visible through blocks: beams and labels (name + distance), pinned inside the visible range
  for far structures, and outline boxes of generated structures nearby.
- Structure screen (`K`): search, filter, sorting by distance or name, dimension selector, per-type on/off,
  Track / Copy coordinates, and a teleport button when cheats are enabled.
- Hot keys: open the screen (`K`), toggle HUD (`H`), toggle world highlights (`J`), track next structure (`G`).
- Client config (`config/structurecompass-client.toml`) editable from the NeoForge mod list.
- English and Russian translations; readable fallback names for structures of other mods.
- Optional networking: on servers without the mod the client disables itself with a single chat message;
  dedicated servers answer that structure search is single-player only.
- Game tests (headless `runGameTestServer`) comparing the locator with vanilla `/locate` semantics and vanilla
  world generation, plus JUnit tests for the math and formatting helpers.

[1.0.0]: https://github.com/alien2003/structure-compass/releases/tag/v1.0.0
