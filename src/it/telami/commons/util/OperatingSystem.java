package it.telami.commons.util;

/**
 * Class representing the type of operating system the JVM is running on.
 * @author Telami
 * @since 1.0.0
 */
public enum OperatingSystem {
    WINDOWS,
    LINUX,
    MAC,
    UNKNOWN;

    public static final OperatingSystem currentOS;
    public static final String currentVersion;
    static {
        final String osn;
        currentOS = switch ((osn = System
                .getProperty("os.name")
                .toLowerCase())
                .substring(0, Math.min(osn
                        .indexOf(' ')
                        & 0x7fffffff, osn
                        .length()))) {
            case "windows" -> WINDOWS;
            case "linux" -> LINUX;
            case "macos", "mac" -> MAC;
            default -> UNKNOWN;
        };
        currentVersion = System.getProperty("os.version");
    }
}
