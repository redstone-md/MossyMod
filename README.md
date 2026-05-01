# MossyMod

MossyMod is a multi-version Fabric mod for recent Minecraft releases that adds mesh-based world discovery and experimental peer-to-peer joins on top of MOSS.

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

## Native Runtime

The build downloads current MOSS native libraries from the latest [redstone-md/moss](https://github.com/redstone-md/moss) GitHub release before packaging.

The produced mod jar bundles:

- `owo-lib`
- `jna`
- `jna-platform`
- platform natives for `libmoss`

Native archives are cached under each Stonecutter node's `build/moss-native-cache`, checked against the release asset SHA-256 digest, and expanded into generated resources. Native binaries are not stored in the repository.

## Development

Requirements:

- Java `21+`
- Fabric Loader for the target Minecraft version

Build every registered Minecraft version with Stonecutter:

```bash
./gradlew buildAllVersions
```

Windows:

```powershell
./gradlew.bat buildAllVersions
```

Build a single target:

```powershell
./gradlew.bat :1.21.11:build
./gradlew.bat :1.21.9:build
```

Release artifacts are written to each version node, for example:

- `versions/1.21.11/build/libs/`
- `versions/1.21.9/build/libs/`

## Repository Layout

- [src/main/java](/D:/code/Mods/Minecraft/Mossy/src/main/java) - mesh, tunnel, Netty bridge, config, common logic
- [src/client/java](/D:/code/Mods/Minecraft/Mossy/src/client/java) - client bootstrap, UI, client mixins
- [src/main/resources](/D:/code/Mods/Minecraft/Mossy/src/main/resources) - Fabric metadata, mixins, assets, bundled natives
- [API.md](/D:/code/Mods/Minecraft/Mossy/API.md) - local API notes for the embedded MOSS integration
- [SHARED_INTEGRATION.md](/D:/code/Mods/Minecraft/Mossy/SHARED_INTEGRATION.md) - additional native/shared-library integration notes

## License

This repository is licensed under the terms in [LICENSE](/D:/code/Mods/Minecraft/Mossy/LICENSE).
