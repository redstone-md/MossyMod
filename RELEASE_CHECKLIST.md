# Release Checklist

## Before Tagging

- Verify the mod still targets Minecraft `1.21.11`
- Run `./gradlew.bat build` or `./gradlew build`
- Confirm the final jar is [build/libs/mossy-1.0.0.jar](/D:/code/Mods/Minecraft/Mossy/build/libs/mossy-1.0.0.jar)
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
