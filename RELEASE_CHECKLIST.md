# Release Checklist

## Before Tagging

- Verify the Stonecutter target list in [settings.gradle](/D:/code/MossyMod/settings.gradle)
- Run `./gradlew.bat buildAllVersions` or `./gradlew buildAllVersions`
- Confirm final jars are produced under `versions/*/build/libs/`
- Confirm the jar contains bundled Java dependencies and `natives/...`

## Native Runtime

- Replace bundled `libmoss` binaries in [src/main/resources/natives](/D:/code/Mods/Minecraft/Mossy/src/main/resources/natives) when the embedded MOSS runtime changes
- Test at least the platforms whose binaries were updated
- Keep the native filenames stable unless the loader is updated too

## Smoke Tests

- Open `Friends`
- Add a bootstrap peer
- Publish an integrated-server world
- Confirm discovery appears on another client
- Attempt a P2P connect and inspect diagnostics/logs

## Documentation

- Update [README.md](/D:/code/Mods/Minecraft/Mossy/README.md) if bundled platforms, runtime expectations, or setup steps changed
- Update [API.md](/D:/code/Mods/Minecraft/Mossy/API.md) and [SHARED_INTEGRATION.md](/D:/code/Mods/Minecraft/Mossy/SHARED_INTEGRATION.md) if the native integration contract changed
