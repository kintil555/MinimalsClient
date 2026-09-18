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

## CI

`.github/workflows/build.yml` builds the mod on every push/PR with JDK 25 and
uploads the resulting jar as a workflow artifact.
