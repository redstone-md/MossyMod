# MossyMod

MossyMod is a Fabric mod for Minecraft `1.21.11` that adds mesh-based world discovery and experimental peer-to-peer joins on top of MOSS.

## Foundation

This project is built on [redstone-md/moss](https://github.com/redstone-md/moss).

Moss is an embeddable P2P mesh core written in Go and exported through CGO as a C-shared library.

## What It Does

- Discovers friend worlds over the MOSS mesh
- Publishes integrated-server worlds into the mesh
- Provides an `owo-lib` UI for friends, settings, diagnostics, and peer bootstrap
- Uses a custom Netty transport over MOSS tunnels for experimental P2P Minecraft traffic

## Current State

The mod is usable for discovery, diagnostics, bootstrap-peer management, and ongoing P2P transport testing.

The networking path is still experimental. Discovery and transport debugging are much more stable than full end-to-end gameplay compatibility, so treat releases as testing builds unless noted otherwise.

## Bundled Runtime

The build is intentionally self-contained.

The produced mod jar bundles:

- `owo-lib`
- `jna`
- `jna-platform`
- platform natives for `libmoss`

Bundled MOSS native libraries currently live in:

- [src/main/resources/natives/windows-x86_64/moss.dll](/D:/code/Mods/Minecraft/Mossy/src/main/resources/natives/windows-x86_64/moss.dll)
- [src/main/resources/natives/linux-x86_64/libmoss.so](/D:/code/Mods/Minecraft/Mossy/src/main/resources/natives/linux-x86_64/libmoss.so)
- [src/main/resources/natives/macos-x86_64/libmoss.dylib](/D:/code/Mods/Minecraft/Mossy/src/main/resources/natives/macos-x86_64/libmoss.dylib)
- [src/main/resources/natives/macos-aarch64/libmoss.dylib](/D:/code/Mods/Minecraft/Mossy/src/main/resources/natives/macos-aarch64/libmoss.dylib)

We intentionally update these bundled natives between builds instead of downloading them at runtime.

## Development

Requirements:

- Java `21+`
- Fabric Loader for Minecraft `1.21.11`

Build:

```bash
./gradlew build
```

Windows:

```powershell
./gradlew.bat build
```

The release artifact is written to:

- [build/libs/mossy-1.0.0.jar](/D:/code/Mods/Minecraft/Mossy/build/libs/mossy-1.0.0.jar)

## Repository Layout

- [src/main/java](/D:/code/Mods/Minecraft/Mossy/src/main/java) - mesh, tunnel, Netty bridge, config, common logic
- [src/client/java](/D:/code/Mods/Minecraft/Mossy/src/client/java) - client bootstrap, UI, client mixins
- [src/main/resources](/D:/code/Mods/Minecraft/Mossy/src/main/resources) - Fabric metadata, mixins, assets, bundled natives
- [API.md](/D:/code/Mods/Minecraft/Mossy/API.md) - local API notes for the embedded MOSS integration
- [SHARED_INTEGRATION.md](/D:/code/Mods/Minecraft/Mossy/SHARED_INTEGRATION.md) - additional native/shared-library integration notes

## License

This repository is licensed under the terms in [LICENSE](/D:/code/Mods/Minecraft/Mossy/LICENSE).
