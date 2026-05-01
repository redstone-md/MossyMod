# Release Checklist

## Before Tagging

- Verify the Stonecutter target list in [settings.gradle](/D:/code/MossyMod/settings.gradle)
- Run `./gradlew.bat buildAllVersions` or `./gradlew buildAllVersions`
- Confirm final jars are produced under `versions/*/build/libs/`
- Confirm the jar contains bundled Java dependencies and `natives/...`

## Native Runtime

- Confirm `downloadMossNatives` downloads from the expected latest [redstone-md/moss](https://github.com/redstone-md/moss) release
- Confirm downloaded archives pass the GitHub release asset SHA-256 digest check
- Confirm final jars contain `natives/linux-x86_64`, `natives/windows-x86_64`, `natives/macos-x86_64`, and `natives/macos-aarch64`
- Keep generated native resource filenames stable unless [MossNativeLoader](/D:/code/MossyMod/src/main/java/md/redstone/moss/MossNativeLoader.java) is updated too

## Smoke Tests

- Open `Friends`
- Add a bootstrap peer
- Publish an integrated-server world
- Confirm discovery appears on another client
- Attempt a P2P connect and inspect diagnostics/logs

## Documentation

- Update [README.md](/D:/code/Mods/Minecraft/Mossy/README.md) if bundled platforms, runtime expectations, or setup steps changed
- Update [API.md](/D:/code/Mods/Minecraft/Mossy/API.md) and [SHARED_INTEGRATION.md](/D:/code/Mods/Minecraft/Mossy/SHARED_INTEGRATION.md) if the native integration contract changed
