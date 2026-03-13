package md.redstone.moss;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import md.redstone.Mossy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Handles loading and extraction of MOSS native library.
 * Extracts platform-specific library from mod resources to disk,
 * then loads it via JNA.
 */
final class MossNativeLoader {
    private static final Path EXTRACT_DIR = Path.of("config", "mossy-native", "moss");
    private static final Path KEYSTORE_FILE = Path.of("config", "mossy-state", "moss-identity.bin");
    private static volatile MossNative nativeApi;
    private static volatile boolean keyStoreInstalled;
    private static MossNative.MossKeyStoreLoadCallback loadCallback;
    private static MossNative.MossKeyStoreSaveCallback saveCallback;

    private MossNativeLoader() {
    }

    static synchronized MossNative load() {
        if (nativeApi != null) {
            return nativeApi;
        }

        try {
            System.setProperty("jna.nosys", "true");
            Path extracted = extractNativeLibrary();
            nativeApi = Native.load(extracted.toAbsolutePath().toString(), MossNative.class);
            installKeyStore(nativeApi);
            return nativeApi;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load libmoss native library", e);
        }
    }

    private static void installKeyStore(MossNative api) throws IOException {
        if (keyStoreInstalled) {
            return;
        }

        Files.createDirectories(KEYSTORE_FILE.getParent());
        loadCallback = (buffer, capacity) -> {
            try {
                if (!Files.exists(KEYSTORE_FILE)) {
                    return 0;
                }
                byte[] bytes = Files.readAllBytes(KEYSTORE_FILE);
                if (bytes.length == 0 || bytes.length > capacity) {
                    return 0;
                }
                buffer.write(0, bytes, 0, bytes.length);
                return bytes.length;
            } catch (Exception e) {
                Mossy.LOGGER.error("Failed to load Moss identity from {}", KEYSTORE_FILE, e);
                return 0;
            }
        };
        saveCallback = (data, len) -> {
            try {
                byte[] bytes = data.getByteArray(0, len);
                Files.write(KEYSTORE_FILE, bytes);
            } catch (Exception e) {
                Mossy.LOGGER.error("Failed to persist Moss identity to {}", KEYSTORE_FILE, e);
            }
        };

        int rc = api.Moss_SetKeyStore(loadCallback, saveCallback);
        if (rc != 0) {
            throw new IllegalStateException("Moss_SetKeyStore failed with code " + rc);
        }
        keyStoreInstalled = true;
    }

    private static Path extractNativeLibrary() throws IOException {
        String platformDir = detectPlatformDir();
        String fileName = detectLibraryFileName();
        String resourcePath = "natives/" + platformDir + "/" + fileName;
        Path targetDir = EXTRACT_DIR.resolve(platformDir);
        Path targetFile = targetDir.resolve(fileName);

        Files.createDirectories(targetDir);
        try (InputStream input = MossNativeLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Native resource not found: " + resourcePath);
            }
            Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile;
    }

    static byte[] readOwnedBytes(Pointer pointer, int len) {
        if (pointer == null || Pointer.nativeValue(pointer) == 0L || len <= 0) {
            return new byte[0];
        }
        try {
            return pointer.getByteArray(0, len);
        } finally {
            if (nativeApi != null) {
                nativeApi.Moss_Free(pointer);
            }
        }
    }

    static String readOwnedString(Pointer pointer) {
        if (pointer == null || Pointer.nativeValue(pointer) == 0L) {
            return null;
        }
        try {
            return pointer.getString(0);
        } finally {
            if (nativeApi != null) {
                nativeApi.Moss_Free(pointer);
            }
        }
    }

    private static String detectPlatformDir() {
        String arch = normalizeArch(Platform.ARCH);
        if (Platform.isWindows()) {
            return "windows-" + arch;
        }
        if (Platform.isLinux()) {
            return "linux-" + arch;
        }
        if (Platform.isMac()) {
            return "macos-" + arch;
        }
        throw new IllegalStateException("Unsupported platform for libmoss: " + Platform.getOSType() + "/" + Platform.ARCH);
    }

    private static String detectLibraryFileName() {
        if (Platform.isWindows()) {
            return "moss.dll";
        }
        if (Platform.isLinux()) {
            return "libmoss.so";
        }
        if (Platform.isMac()) {
            return "libmoss.dylib";
        }
        throw new IllegalStateException("Unsupported platform for libmoss");
    }

    private static String normalizeArch(String arch) {
        if (arch == null) {
            return "x86_64";
        }
        return switch (arch.toLowerCase()) {
            case "amd64", "x86-64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch.toLowerCase();
        };
    }
}
