# Building the Alpha Snapshot

The public snapshot contains the production implementation and resources for both loaders. Private acceptance worlds, crash fixtures, test helper mods, logs, local dependency binaries and desktop automation are not included.

Private acceptance and benchmark tasks referenced in development scripts are not part of the public test suite. Their fixtures are excluded; use the production jar targets below for this source snapshot.

Use JDK 17 for Forge 1.20.1 and JDK 21 for NeoForge 1.21.1. The checked-in Gradle wrapper selects the toolchain; Java toolchain auto-download is disabled.

```powershell
.\gradlew.bat :forge-1201:jar :neoforge-1211:jar
```

External integration jars used at compile time must be supplied in a local `deps` directory. The exact coordinates and filenames are listed in each loader's `build.gradle`. They are compile-only development inputs, not additional mandatory runtime dependencies and not redistributed in this repository.

In particular, the current snapshot expects the named ACO development API, ME Access virtual-cell API, Applied Mekanistics, GTCEu, LDLib and other integration development jars recorded in those scripts. This is not a claim that an arbitrary newer jar is API-compatible. Use the published downloads for play; do not infer runtime or recovery acceptance from a successful local source build.

AE2 is the only mandatory addon at runtime. Check each download's Minecraft/loader version and its dependency ranges. ACO remains optional.
