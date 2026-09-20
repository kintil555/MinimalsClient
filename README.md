# Minimals — Lightweight client-side HUD (Fabric)

Minimal, client-only Fabric mod scaffold for Minecraft 26.2 (Chaos Cubed).

Features:
- Minimal HUD with FPS and coordinates
- Toggle HUD with the Right Shift key
- Simple, modern rounded UI aesthetic and Minecraft font

Prerequisites:
- Java 25
- Gradle wrapper (add `gradlew`, `gradlew.bat`, and `gradle/wrapper/` yourself,
  e.g. `gradle wrapper --gradle-version 8.14`)
- Fabric Loader 0.19.3+ and Fabric API 0.156.0+26.2 for Minecraft 26.2

Mappings: this project uses Fabric Loom's default (Mojang official mappings).
Minecraft 26.x ships unobfuscated, so Yarn mappings are no longer used or needed.

Build (from project root):
```bash
./gradlew build
```

Drop the generated jar in your Minecraft `mods` folder for a client with Fabric Loader and Fabric API installed.

## Replay

Records a session and plays it back in its own world, with a timeline and FOV control.

- **Recording**: everything the server sends is buffered from the moment you join. Press
  `RShift`, then the record button (right of *Spectate*) to mark the start of a clip and press it
  again (or leave the world) to save it. A red `REC` timer shows in the top-left while recording.
- **Playing**: on the title screen press the small `R` button (right of *Singleplayer*), pick a
  replay and press *Play*. You fly as a spectator; the recorded player's teleports are ignored.
- **Timeline** (opens automatically, reopen with `R`): play/pause (`Space`), speed
  (0.25x-8x), scrub bar (`Left`/`Right` jump 5s), and a FOV slider (mouse wheel also changes FOV;
  right-click the FOV label to go back to the game's FOV). Esc closes the bar to fly freely.
- Files are `.mreplay` in `config/minimals/replays/`.

Limits: seeking backwards rebuilds the world (takes a moment). Replays only work with the same
Minecraft version they were recorded on. After saving a clip the buffer keeps running, so you can
record another clip in the same session, but a clip can only start from the join onward.

## CI

`.github/workflows/build.yml` builds the mod on every push/PR with JDK 25 and
uploads the resulting jar as a workflow artifact.
