# LoreFinder++

Meteor Client addon for **Minecraft 1.21.1**, **1.21.4**, and **1.21.11** (Fabric).

## Modules

| Module | Status | Description |
|--------|--------|-------------|
| **SignFinder** | Available | ESP for signs; **Old Sign Finder** filters by years parsed from sign text. |
| **NamedEntityFinder** | Available | ESP for entities with a custom name in render distance. |
| **AncientBuildsFinder** | Available | Highlights chunks that contain enough ancient build blocks from your list. |
| **IllegalsFinder** | Available | ESP for illegal block states and placements in render distance. |
| **LowMapIDFinder** | Available | ESP for filled maps with a map ID below a threshold (item frames, ground drops). |
| ItemFinder | Planned | — |

## Build

Requires **Java 21** and the matching [Meteor Client](https://meteorclient.com) build for your Minecraft version.

```bash
# Active version (see stonecutter.gradle.kts, default 1.21.4)
./gradlew buildActive

# All supported versions
./gradlew buildAllAndCollect

# One version
./gradlew :1.21.1:build
./gradlew :1.21.4:build
./gradlew :1.21.11:build
```

Output jars: `build/libs/0.1.0/lorefinder-0.1.0+mc<version>.jar`

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0).

## Usage

1. Install [Meteor Client](https://meteorclient.com) for your Minecraft version (1.21.1, 1.21.4, or 1.21.11).
2. Place the matching `lorefinder-0.1.0+mc<version>.jar` in your `mods` folder.
3. In Meteor → **LoreFinder++** → enable a module.

### AncientBuildsFinder

Scans loaded chunks in render distance and draws a green outline around matching chunks.

- **blocks** — editable list (defaults include mossy cobblestone, cobblestone, stone bricks, oak wood, signs, torch, etc.).
- **required-types** — how many *different* blocks from the list must appear in the same chunk.

### IllegalsFinder

Scans block positions in loaded chunks and ESP-marks violations. Toggle each check under **Checks**:

| Setting | Examples |
|---------|----------|
| **invalid-placement** | Flowers on gravel, saplings on stone, crops on invalid blocks |
| **floating-lava** | Lava with air (or non-solid) below |
| **floating-water** | Same for water (off by default — common in oceans) |
| **orphan-beds** | Half of a bed without its partner |
| **orphan-double-blocks** | Tall grass / large plants missing their other half |
| **floating-attached** | Torches, ladders, levers, lanterns without support |

- **max-markers** — cap ESP boxes drawn (default 256); does **not** limit scan cost.
- **scan-radius** — chunk radius to scan (default **4**, not full render distance).
- **chunks-per-tick** — spread work over time (default **1**; raise only if you have headroom).
- **rescan-interval** — full re-queue period in ticks (default **400** ≈ 20s; **0** = new chunks only).
- **tracers** — optional lines to each marker.

Works in **singleplayer and multiplayer** (client chunk data). If FPS tanks on join, lower **scan-radius** and keep **chunks-per-tick** at 1.

### SignFinder / Old Sign Finder

Sign text is visible on the client, so this works in **singleplayer and multiplayer**.

- **old-sign-finder** — only highlight signs whose lines contain dates matching the filter.
- **year-threshold** — `2010` through the current calendar year (2b2t-era range).
- **compare-mode** — default **Any before** (`<2025`): any plausible year in the text strictly before the threshold.
- Years are read only as **four consecutive digits**, between **2010** and the current year.
- **tracers** — lines from your crosshair to matching signs.

### LowMapIDFinder

- **map-id-max** — highlight maps with ID **below** this value (default `50000`). IDs must be **≥ 1** (ID `0` is treated as invalid/placeholder).
- **item-frames** — uses the client’s loaded **map states** (frame entity → real map ID), not the item stack alone.
- **ground-items** — uses the stack only when it has `MAP_ID` and the client has that map state loaded.
- Works in **multiplayer** for frames and dropped maps.

### NamedEntityFinder

- Highlights entities with a **custom name** (`hasCustomName()`).
- **tracers** — optional.
