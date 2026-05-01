package md.redstone.gui;

import md.redstone.config.MossyConfig;
import md.redstone.moss.MossManager;

import java.util.Base64;

final class FriendAccessInfo {
    private static final int PUBLIC_KEY_BYTES = 32;
    private static final String JOIN_CODE_PREFIX = "mossy:";
    private static final String URI_PREFIX = "mossy://";
    private static final String MINECRAFT_PROTOCOL_SUFFIX = "/minecraft";

    private FriendAccessInfo() {
    }

    static String friendCodeValue() {
        return MossManager.getInstance().getPublicKeyBase64();
    }

    static String shareCodeValue() {
        String code = friendCodeValue();
        return code.isBlank() ? "" : JOIN_CODE_PREFIX + code;
    }

    static String friendCodeText() {
        String code = shareCodeValue();
        return code.isBlank() ? "Starting Mossy..." : code;
    }

    static String manualPortText() {
        return "Fallback port: " + MossyConfig.getInstance().listenPort;
    }

    static String manualAddressHint() {
        return "If the join code cannot reach you, share your reachable IP or host with this port.";
    }

    static boolean isValidFriendCode(String value) {
        return !parseFriendCode(value).isBlank();
    }


    static String parseFriendCode(String value) {
        if (value == null) {
            return "";
        }

        String candidate = value.trim();
        if (candidate.startsWith(URI_PREFIX)) {
            candidate = candidate.substring(URI_PREFIX.length());
            if (candidate.endsWith(MINECRAFT_PROTOCOL_SUFFIX)) {
                candidate = candidate.substring(0, candidate.length() - MINECRAFT_PROTOCOL_SUFFIX.length());
            }
        } else if (candidate.startsWith(JOIN_CODE_PREFIX)) {
            candidate = candidate.substring(JOIN_CODE_PREFIX.length());
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(candidate);
            return decoded.length == PUBLIC_KEY_BYTES ? candidate : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
