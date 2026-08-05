package com.embedize.datapack;

import java.util.Locale;

/**
 * Version string helpers shared by Modrinth resolution (no Bukkit dependency).
 */
public final class VersionUtil {

    private VersionUtil() {
    }

    public static boolean versionMatches(String server, String gameVersion) {
        String s = normalizeVersion(server);
        String g = normalizeVersion(gameVersion);
        if (s.isEmpty() || g.isEmpty()) {
            return false;
        }
        if (s.equals(g)) {
            return true;
        }
        if (s.startsWith(g + ".") || g.startsWith(s + ".")) {
            return true;
        }
        if (g.endsWith(".x")) {
            String prefix = g.substring(0, g.length() - 2);
            return s.equals(prefix) || s.startsWith(prefix + ".");
        }
        String[] sParts = s.split("\\.");
        String[] gParts = g.split("\\.");
        if (sParts.length >= 2 && gParts.length >= 2
                && sParts[0].equals(gParts[0]) && sParts[1].equals(gParts[1])) {
            return gParts.length == 2 || sParts.length == 2
                    || (sParts.length >= 3 && gParts.length >= 3 && sParts[2].equals(gParts[2]));
        }
        return false;
    }

    public static String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        int buildIdx = v.indexOf(".build.");
        if (buildIdx > 0) {
            v = v.substring(0, buildIdx);
        }
        int dash = v.indexOf('-');
        if (dash > 0) {
            v = v.substring(0, dash);
        }
        if (v.startsWith("v")) {
            v = v.substring(1);
        }
        return v;
    }
}
