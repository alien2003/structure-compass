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
