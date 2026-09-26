package df.root;

import android.os.Build;
import android.system.Os;
import android.util.Log;

import java.io.File;

/**
 * Pre-flight self-check for the DirtyFrag chain.
 *
 * The exploit patches three fixed paths and loads a KMI-specific kernel module,
 * so a wrong device or firmware fails in confusing ways (or panics the kernel).
 * This runs before anything is touched and refuses to continue when a hard
 * requirement is missing.
 *
 * Added for the SM-S938B (Galaxy S25 Ultra) port. The bundled ksud asset is the
 * Samsung KDP/RKP/DEFEX build for BP4A.251205.006.S938BXXS9CZE1; the profile
 * constants below are only used to warn, never to hard-block, so the same APK
 * still runs on a neighbouring S938B firmware.
 */
final class DeviceCheck {

    static final String TAG = "dfroot";

    /** Firmware this build's ksud asset was built and validated for. */
    static final String PROFILE_MODEL   = "SM-S938B";
    static final String PROFILE_DEVICE  = "pa3q";
    static final String PROFILE_DISPLAY = "BP4A.251205.006.S938BXXS9CZE1";
    static final String PROFILE_KMI     = "android15-6.6";
    static final String PROFILE_RELEASE = "6.6.98-android15-8-pe17667d-abogkiS938BXXS9CZE1-4k";

    /** Every path the chain opens; a missing one is fatal. */
    static final String[] REQUIRED_PATHS = {
        "/vendor/lib64/libstagefrighthw.so",           // LKM page-cache target (exp.c, libc.S)
        "/vendor/bin/modprobe",                        // stage1 exec target (libcxx.S, libc.S)
        "/apex/com.android.runtime/bin/crash_dump64",  // splice helper (exp.c)
        "/system/lib64/libc.so",                       // __libc_init hook
        "/system/lib64/libc++.so",                     // ostream sentry hook
        "/system/bin/logcat",                          // ksud bind-mount target
    };

    private DeviceCheck() {}

    static String release() {
        try {
            return Os.uname().release;
        } catch (Throwable t) {
            return "";
        }
    }

    /** "android15-6.6" derived from uname -r, or "" when unparseable. */
    static String kmi() {
        String r = release();
        int a = r.indexOf("android");
        if (a < 0) return "";
        String rest = r.substring(a + 7);
        int dash = rest.indexOf('-');
        String rel = dash > 0 ? rest.substring(0, dash) : rest;
        try {
            Integer.parseInt(rel);
        } catch (Throwable t) {
            return "";
        }
        String[] parts = r.split("\\.");
        if (parts.length < 2) return "";
        try {
            return "android" + rel + "-" + Integer.parseInt(parts[0]) + "." + Integer.parseInt(parts[1]);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Comma-joined list of required paths that do not exist. */
    static String missingPaths() {
        StringBuilder sb = new StringBuilder();
        for (String p : REQUIRED_PATHS) {
            if (!new File(p).exists()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p);
            }
        }
        return sb.toString();
    }

    /** Hard requirements: there is no point running without these. */
    static boolean criticalOk() {
        return missingPaths().isEmpty() && !kmi().isEmpty();
    }

    /** True when this is the exact firmware the bundled ksud was built for. */
    static boolean exactProfile() {
        return PROFILE_MODEL.equals(Build.MODEL) && PROFILE_RELEASE.equals(release());
    }

    static String report() {
        String rel = release();
        String kmi = kmi();
        String missing = missingPaths();
        StringBuilder sb = new StringBuilder();
        sb.append("* device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
          .append(" (").append(Build.DEVICE).append(")\n");
        sb.append("* build : ").append(Build.DISPLAY).append('\n');
        sb.append("* kernel: ").append(rel).append('\n');
        sb.append("* kmi   : ").append(kmi.isEmpty() ? "UNKNOWN" : kmi).append('\n');
        sb.append(missing.isEmpty()
                ? "* paths: all " + REQUIRED_PATHS.length + " required paths present\n"
                : "* paths: MISSING -> " + missing + "\n");
        if (exactProfile()) {
            sb.append("* profile: exact match (").append(PROFILE_DISPLAY).append(")\n");
        } else {
            sb.append("* profile: NOT the validated firmware (")
              .append(PROFILE_MODEL).append(" / ").append(PROFILE_RELEASE).append(")\n");
            sb.append("           continuing, but the bundled ksud is only validated for that pair\n");
        }
        if (!PROFILE_KMI.equals(kmi)) {
            sb.append("* warn  : kernel KMI is ").append(kmi.isEmpty() ? "unknown" : kmi)
              .append(", bundled ksud embeds ").append(PROFILE_KMI).append('\n');
        }
        return sb.toString();
    }

    /** Logs the report; returns true when it is safe to start patching. */
    static boolean preflight(String where) {
        Log.i(TAG, "preflight (" + where + ")\n" + report());
        if (!criticalOk()) {
            Log.e(TAG, "preflight failed in " + where + ": required paths missing ("
                    + missingPaths() + ") or kernel KMI unparseable - refusing to patch anything");
            return false;
        }
        return true;
    }
}
